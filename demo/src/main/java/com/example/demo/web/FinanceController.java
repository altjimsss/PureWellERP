package com.example.demo.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class FinanceController {

	private final JdbcTemplate jdbcTemplate;

	public FinanceController(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@GetMapping("/modules/finance")
	public String financeModule(Model model) {
		model.addAttribute("siteTitle", "Finance and Accounting Module");
		model.addAttribute("moduleName", "Finance and Accounting");
		try {
			model.addAttribute("snapshot", loadSnapshot());
			model.addAttribute("recentExpenses", loadRecentExpenses());
			model.addAttribute("recentRecords", loadRecentRecords());
			model.addAttribute("expenseBreakdown", loadExpenseBreakdown());
			model.addAttribute("monthlyReports", loadMonthlyReports());
			model.addAttribute("dbAvailable", true);
		} catch (DataAccessException ex) {
			model.addAttribute("snapshot", FinanceSnapshot.empty());
			model.addAttribute("recentExpenses", List.of());
			model.addAttribute("recentRecords", List.of());
			model.addAttribute("expenseBreakdown", List.of());
			model.addAttribute("monthlyReports", List.of());
			model.addAttribute("dbAvailable", false);
		}
		return "finance-module";
	}

	private FinanceSnapshot loadSnapshot() {
		Double revenue = jdbcTemplate.queryForObject(
				"""
				select coalesce(sum(amount),0)
				from financial_records
				where record_type = 'Revenue'
				  and date >= current_date - interval '30 days'
				""",
				Double.class);
		Double expenses = jdbcTemplate.queryForObject(
				"""
				select coalesce(sum(amount),0)
				from expenses
				where date >= current_date - interval '30 days'
				""",
				Double.class);
		Double avgDailyExpense = jdbcTemplate.queryForObject(
				"""
				select coalesce(avg(daily_total),0)
				from (
					select date, sum(amount) as daily_total
					from expenses
					where date >= current_date - interval '30 days'
					group by date
				) totals
				""",
				Double.class);
		Integer expenseCount = jdbcTemplate.queryForObject(
				"""
				select count(*)
				from expenses
				where date >= current_date - interval '30 days'
				""",
				Integer.class);
		Integer recordCount = jdbcTemplate.queryForObject(
				"""
				select count(*)
				from financial_records
				where date >= current_date - interval '30 days'
				""",
				Integer.class);

		double revenueValue = valueOrZero(revenue);
		double expenseValue = valueOrZero(expenses);
		double net = revenueValue - expenseValue;
		return new FinanceSnapshot(
				round2(revenueValue),
				round2(expenseValue),
				round2(net),
				round2(valueOrZero(avgDailyExpense)),
				valueOrZero(expenseCount),
				valueOrZero(recordCount),
				"Last 30 days"
		);
	}

	private List<ExpenseRow> loadRecentExpenses() {
		return jdbcTemplate.query(
				"""
				select id, expense_type, amount, date, description
				from expenses
				order by date desc, id desc
				limit 6
				""",
				(rs, rowNum) -> new ExpenseRow(
						rs.getInt("id"),
						rs.getString("expense_type"),
						rs.getBigDecimal("amount"),
						rs.getDate("date").toLocalDate(),
						rs.getString("description")
				)
		);
	}

	private List<FinancialRecordRow> loadRecentRecords() {
		return jdbcTemplate.query(
				"""
				select id, record_type, amount, date, notes
				from financial_records
				order by date desc, id desc
				limit 6
				""",
				(rs, rowNum) -> new FinancialRecordRow(
						rs.getInt("id"),
						rs.getString("record_type"),
						rs.getBigDecimal("amount"),
						rs.getDate("date").toLocalDate(),
						rs.getString("notes")
				)
		);
	}

	private List<ExpenseTypeRow> loadExpenseBreakdown() {
		List<ExpenseTypeTotal> raw = jdbcTemplate.query(
				"""
				select expense_type, sum(amount) as total
				from expenses
				where date >= current_date - interval '30 days'
				group by expense_type
				order by total desc
				""",
				(rs, rowNum) -> new ExpenseTypeTotal(
						rs.getString("expense_type"),
						rs.getBigDecimal("total")
				)
		);
		double total = raw.stream()
				.map(ExpenseTypeTotal::total)
				.mapToDouble(BigDecimal::doubleValue)
				.sum();

		return raw.stream()
				.map(item -> {
					double value = item.total().doubleValue();
					int percent = total == 0 ? 0 : (int) Math.round((value * 100.0) / total);
					return new ExpenseTypeRow(item.type(), item.total(), percent);
				})
				.toList();
	}

	private List<MonthlyReportRow> loadMonthlyReports() {
		List<MonthlyReportRaw> raw = jdbcTemplate.query(
				"""
				select m.month_start,
				       to_char(m.month_start, 'Mon YYYY') as label,
				       coalesce((
				         select sum(amount)
				         from financial_records fr
				         where fr.record_type = 'Revenue'
				           and date_trunc('month', fr.date) = m.month_start
				       ),0) as revenue,
				       coalesce((
				         select sum(amount)
				         from expenses e
				         where date_trunc('month', e.date) = m.month_start
				       ),0) as expenses
				from (
					select date_trunc('month', current_date) - interval '5 months'
					       + (interval '1 month' * gs) as month_start
					from generate_series(0,5) as gs
				) m
				order by m.month_start
				""",
				(rs, rowNum) -> new MonthlyReportRaw(
						rs.getDate("month_start").toLocalDate(),
						rs.getString("label"),
						rs.getBigDecimal("revenue"),
						rs.getBigDecimal("expenses")
				)
		);

		double max = raw.stream()
				.map(item -> Math.max(item.revenue().doubleValue(), item.expenses().doubleValue()))
				.max(Comparator.naturalOrder())
				.orElse(1.0);

		return raw.stream()
				.map(item -> {
					double revenue = item.revenue().doubleValue();
					double expenses = item.expenses().doubleValue();
					double net = revenue - expenses;
					int revenuePercent = max == 0 ? 0 : (int) Math.round((revenue * 100.0) / max);
					int expensePercent = max == 0 ? 0 : (int) Math.round((expenses * 100.0) / max);
					return new MonthlyReportRow(
							item.monthStart(),
							item.label(),
							item.revenue(),
							item.expenses(),
							BigDecimal.valueOf(round2(net)),
							revenuePercent,
							expensePercent
					);
				})
				.toList();
	}

	private static int valueOrZero(Integer value) {
		return value == null ? 0 : value;
	}

	private static double valueOrZero(Double value) {
		return value == null ? 0 : value;
	}

	private static double round2(double value) {
		return Math.round(value * 100.0) / 100.0;
	}

	public record FinanceSnapshot(
			double totalRevenue,
			double totalExpenses,
			double netIncome,
			double avgDailyExpense,
			int expenseCount,
			int recordCount,
			String periodLabel
	) {
		public static FinanceSnapshot empty() {
			return new FinanceSnapshot(0, 0, 0, 0, 0, 0, "Last 30 days");
		}
	}

	public record ExpenseRow(
			int id,
			String type,
			BigDecimal amount,
			LocalDate date,
			String description
	) { }

	public record FinancialRecordRow(
			int id,
			String type,
			BigDecimal amount,
			LocalDate date,
			String notes
	) { }

	private record ExpenseTypeTotal(String type, BigDecimal total) { }

	public record ExpenseTypeRow(String type, BigDecimal total, int percent) { }

	private record MonthlyReportRaw(LocalDate monthStart, String label, BigDecimal revenue, BigDecimal expenses) { }

	public record MonthlyReportRow(
			LocalDate monthStart,
			String label,
			BigDecimal revenue,
			BigDecimal expenses,
			BigDecimal net,
			int revenuePercent,
			int expensePercent
	) { }
}
