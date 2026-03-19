package com.example.demo.web;

import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class FinanceController {

	private final JdbcTemplate jdbcTemplate;

	public FinanceController(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@GetMapping("/modules/finance")
	public String financeModule(Model model, HttpSession session) {
		UserProfile userProfile = SessionUtil.getUser(session);
		if (userProfile == null) {
			return "redirect:/login";
		}
		model.addAttribute("siteTitle", "Finance and Accounting Module");
		model.addAttribute("moduleName", "Finance and Accounting");
		model.addAttribute("userProfile", userProfile);
		return "finance-module";
	}

	@GetMapping("/modules/finance/report")
	public void downloadFinanceReport(
			HttpServletResponse response,
			@RequestParam(name = "format", defaultValue = "csv") String format,
			@RequestParam(name = "include", required = false) List<String> include,
			@RequestParam(name = "expensesPeriod", defaultValue = "30") String expensesPeriod,
			@RequestParam(name = "expensesView", defaultValue = "recent") String expensesView,
			@RequestParam(name = "recordsPeriod", defaultValue = "30") String recordsPeriod,
			@RequestParam(name = "recordsView", defaultValue = "recent") String recordsView,
			@RequestParam(name = "reportsPeriod", defaultValue = "6") String reportsPeriod
	) throws IOException {
		Integer expensesDays = parseDaysPeriod(expensesPeriod, 30);
		Integer recordsDays = parseDaysPeriod(recordsPeriod, 30);
		boolean expensesAll = isViewAll(expensesView);
		boolean recordsAll = isViewAll(recordsView);
		int reportsMonths = parseMonthsPeriod(reportsPeriod);
		Set<String> included = normalizeIncludes(include);

		FinanceSnapshot snapshot = loadSnapshot();
		FinanceInsights insights = loadInsights();
		List<ExpenseRow> expenses = loadRecentExpenses(expensesDays, expensesAll ? null : 6);
		List<FinancialRecordRow> records = loadRecentRecords(recordsDays, recordsAll ? null : 6);
		List<MonthlyReportRow> reports = loadMonthlyReports(reportsMonths);

		String safeFormat = normalizeFormat(format);
		String filename = "finance-report-" + LocalDate.now() + "." + safeFormat;

		if ("xlsx".equals(safeFormat)) {
			writeExcelReport(response, filename, included, snapshot, insights, expenses, records, reports);
			return;
		}
		if ("pdf".equals(safeFormat)) {
			writePdfReport(response, filename, included, snapshot, insights, expenses, records, reports);
			return;
		}
		writeCsvReport(response, filename, included, snapshot, insights, expenses, records, reports);
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

	private FinanceInsights loadInsights() {
		Double revenueLast7 = jdbcTemplate.queryForObject(
				"""
				select coalesce(sum(amount),0)
				from financial_records
				where record_type = 'Revenue'
				  and date >= current_date - interval '7 days'
				""",
				Double.class);
		Double revenuePrev7 = jdbcTemplate.queryForObject(
				"""
				select coalesce(sum(amount),0)
				from financial_records
				where record_type = 'Revenue'
				  and date >= current_date - interval '14 days'
				  and date < current_date - interval '7 days'
				""",
				Double.class);
		Double expenseLast7 = jdbcTemplate.queryForObject(
				"""
				select coalesce(sum(amount),0)
				from expenses
				where date >= current_date - interval '7 days'
				""",
				Double.class);
		Double expensePrev7 = jdbcTemplate.queryForObject(
				"""
				select coalesce(sum(amount),0)
				from expenses
				where date >= current_date - interval '14 days'
				  and date < current_date - interval '7 days'
				""",
				Double.class);

		ExpenseTypeTotal topExpense = jdbcTemplate.query(
				"""
				select expense_type, sum(amount) as total
				from expenses
				where date >= current_date - interval '7 days'
				group by expense_type
				order by total desc
				limit 1
				""",
				(rs, rowNum) -> new ExpenseTypeTotal(
						rs.getString("expense_type"),
						rs.getBigDecimal("total")
				)
		).stream().findFirst().orElse(new ExpenseTypeTotal("None", BigDecimal.ZERO));

		DailyRevenueSummary bestDay = jdbcTemplate.query(
				"""
				select to_char(date, 'Dy') as label, sum(amount) as total
				from financial_records
				where record_type = 'Revenue'
				  and date >= current_date - interval '7 days'
				group by to_char(date, 'Dy')
				order by total desc
				limit 1
				""",
				(rs, rowNum) -> new DailyRevenueSummary(
						rs.getString("label").trim(),
						rs.getBigDecimal("total")
				)
		).stream().findFirst().orElse(new DailyRevenueSummary("N/A", BigDecimal.ZERO));

		double revenueChange = percentChange(valueOrZero(revenueLast7), valueOrZero(revenuePrev7));
		double expenseChange = percentChange(valueOrZero(expenseLast7), valueOrZero(expensePrev7));
		double netLast7 = valueOrZero(revenueLast7) - valueOrZero(expenseLast7);
		double netPrev7 = valueOrZero(revenuePrev7) - valueOrZero(expensePrev7);
		String netTrend = netLast7 >= netPrev7 ? "Improving" : "Declining";

		return new FinanceInsights(
				round2(revenueChange),
				round2(expenseChange),
				netTrend,
				topExpense.type(),
				topExpense.total(),
				bestDay.label(),
				bestDay.total()
		);
	}

	private List<ExpenseRow> loadRecentExpenses(Integer days, Integer limit) {
		StringBuilder sql = new StringBuilder(
				"select id, expense_type, amount, date, description from expenses");
		Object[] params = new Object[] {};
		if (days != null) {
			sql.append(" where date >= current_date - (? * interval '1 day')");
			params = new Object[] { days };
		}
		sql.append(" order by date desc, id desc");
		if (limit != null) {
			sql.append(" limit ").append(limit);
		}
		return jdbcTemplate.query(
				sql.toString(),
				(rs, rowNum) -> new ExpenseRow(
						rs.getInt("id"),
						rs.getString("expense_type"),
						rs.getBigDecimal("amount"),
						rs.getDate("date").toLocalDate(),
						rs.getString("description")
				),
				params
		);
	}

	private List<FinancialRecordRow> loadRecentRecords(Integer days, Integer limit) {
		StringBuilder sql = new StringBuilder(
				"select id, record_type, amount, date, notes from financial_records");
		Object[] params = new Object[] {};
		if (days != null) {
			sql.append(" where date >= current_date - (? * interval '1 day')");
			params = new Object[] { days };
		}
		sql.append(" order by date desc, id desc");
		if (limit != null) {
			sql.append(" limit ").append(limit);
		}
		return jdbcTemplate.query(
				sql.toString(),
				(rs, rowNum) -> new FinancialRecordRow(
						rs.getInt("id"),
						rs.getString("record_type"),
						rs.getBigDecimal("amount"),
						rs.getDate("date").toLocalDate(),
						rs.getString("notes")
				),
				params
		);
	}

	private List<ExpenseTypeRow> loadExpenseBreakdown(Integer days) {
		StringBuilder sql = new StringBuilder(
				"select expense_type, sum(amount) as total from expenses");
		Object[] params = new Object[] {};
		if (days != null) {
			sql.append(" where date >= current_date - (? * interval '1 day')");
			params = new Object[] { days };
		}
		sql.append(" group by expense_type order by total desc");

		List<ExpenseTypeTotal> raw = jdbcTemplate.query(
				sql.toString(),
				(rs, rowNum) -> new ExpenseTypeTotal(
						rs.getString("expense_type"),
						rs.getBigDecimal("total")
				),
				params
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

	private List<MonthlyReportRow> loadMonthlyReports(int monthsCount) {
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
					select date_trunc('month', current_date) - ((? - 1) * interval '1 month')
					       + (interval '1 month' * gs) as month_start
					from generate_series(0, ? - 1) as gs
				) m
				order by m.month_start
				""",
				(rs, rowNum) -> new MonthlyReportRaw(
						rs.getDate("month_start").toLocalDate(),
						rs.getString("label"),
						rs.getBigDecimal("revenue"),
						rs.getBigDecimal("expenses")
				),
				monthsCount,
				monthsCount
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

	private static Integer parseDaysPeriod(String value, int fallback) {
		if (value == null) {
			return fallback;
		}
		String normalized = value.trim().toLowerCase();
		if ("all".equals(normalized)) {
			return null;
		}
		try {
			int parsed = Integer.parseInt(normalized);
			if (parsed == 7 || parsed == 30 || parsed == 90) {
				return parsed;
			}
		} catch (NumberFormatException ignored) {
		}
		return fallback;
	}

	private static boolean isViewAll(String value) {
		return value != null && value.trim().equalsIgnoreCase("all");
	}

	private int parseMonthsPeriod(String value) {
		if (value == null) {
			return 6;
		}
		String normalized = value.trim().toLowerCase();
		if ("all".equals(normalized)) {
			LocalDate earliest = loadEarliestFinanceDate();
			if (earliest == null) {
				return 6;
			}
			YearMonth start = YearMonth.from(earliest);
			YearMonth now = YearMonth.from(LocalDate.now());
			int months = (now.getYear() - start.getYear()) * 12 + (now.getMonthValue() - start.getMonthValue()) + 1;
			return Math.max(months, 1);
		}
		try {
			int parsed = Integer.parseInt(normalized);
			if (parsed == 6 || parsed == 12 || parsed == 24) {
				return parsed;
			}
		} catch (NumberFormatException ignored) {
		}
		return 6;
	}

	private LocalDate loadEarliestFinanceDate() {
		return jdbcTemplate.query(
				"""
				select min(d) as earliest from (
					select min(date) as d from expenses
					union all
					select min(date) as d from financial_records
				) t
				""",
				(rs, rowNum) -> {
					java.sql.Date date = rs.getDate("earliest");
					return date == null ? null : date.toLocalDate();
				}
		).stream().findFirst().orElse(null);
	}

	private static double percentChange(double current, double previous) {
		if (previous == 0) {
			return current == 0 ? 0 : 100;
		}
		return ((current - previous) / previous) * 100.0;
	}

	private static String safeCsv(String input) {
		if (input == null) {
			return "";
		}
		String escaped = input.replace("\"", "\"\"");
		return "\"" + escaped + "\"";
	}

	private static String normalizeFormat(String format) {
		if (format == null) {
			return "csv";
		}
		String normalized = format.trim().toLowerCase();
		if ("xlsx".equals(normalized) || "excel".equals(normalized)) {
			return "xlsx";
		}
		if ("pdf".equals(normalized)) {
			return "pdf";
		}
		return "csv";
	}

	private static Set<String> normalizeIncludes(List<String> include) {
		if (include == null || include.isEmpty()) {
			return Set.of("snapshot", "insights", "expenses", "records", "reports");
		}
		return include.stream()
				.map(value -> value == null ? "" : value.trim().toLowerCase())
				.filter(value -> !value.isBlank())
				.collect(java.util.stream.Collectors.toSet());
	}

	private static boolean includeSection(Set<String> include, String key) {
		return include.contains(key);
	}

	private void writeCsvReport(
			HttpServletResponse response,
			String filename,
			Set<String> include,
			FinanceSnapshot snapshot,
			FinanceInsights insights,
			List<ExpenseRow> expenses,
			List<FinancialRecordRow> records,
			List<MonthlyReportRow> reports
	) throws IOException {
		response.setContentType("text/csv");
		response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");

		StringBuilder csv = new StringBuilder();
		csv.append("Finance and Accounting Report\n");
		csv.append("Generated,").append(LocalDate.now()).append("\n\n");

		if (includeSection(include, "snapshot")) {
			csv.append("Snapshot\n");
			csv.append("Total Revenue,").append(snapshot.totalRevenue()).append("\n");
			csv.append("Total Expenses,").append(snapshot.totalExpenses()).append("\n");
			csv.append("Net Income,").append(snapshot.netIncome()).append("\n");
			csv.append("Average Daily Expense,").append(snapshot.avgDailyExpense()).append("\n");
			csv.append("Expense Entries,").append(snapshot.expenseCount()).append("\n");
			csv.append("Financial Records,").append(snapshot.recordCount()).append("\n");
			csv.append("Period,").append(snapshot.periodLabel()).append("\n\n");
		}

		if (includeSection(include, "insights")) {
			csv.append("Insights (Last 7 Days)\n");
			csv.append("Revenue Change %,").append(insights.revenueChangePercent()).append("\n");
			csv.append("Expense Change %,").append(insights.expenseChangePercent()).append("\n");
			csv.append("Net Trend,").append(insights.netTrendLabel()).append("\n");
			csv.append("Top Expense Category,").append(insights.topExpenseType()).append("\n");
			csv.append("Top Expense Amount,").append(insights.topExpenseAmount()).append("\n");
			csv.append("Best Revenue Day,").append(insights.bestRevenueDay()).append("\n");
			csv.append("Best Revenue Amount,").append(insights.bestRevenueAmount()).append("\n\n");
		}

		if (includeSection(include, "expenses")) {
			csv.append("Expenses\n");
			csv.append("Date,Type,Amount,Description\n");
			for (ExpenseRow row : expenses) {
				csv.append(row.date()).append(",");
				csv.append(safeCsv(row.type())).append(",");
				csv.append(row.amount()).append(",");
				csv.append(safeCsv(row.description())).append("\n");
			}
			csv.append("\n");
		}

		if (includeSection(include, "records")) {
			csv.append("Financial Records\n");
			csv.append("Date,Type,Amount,Notes\n");
			for (FinancialRecordRow row : records) {
				csv.append(row.date()).append(",");
				csv.append(safeCsv(row.type())).append(",");
				csv.append(row.amount()).append(",");
				csv.append(safeCsv(row.notes())).append("\n");
			}
			csv.append("\n");
		}

		if (includeSection(include, "reports")) {
			csv.append("Monthly Reports\n");
			csv.append("Month,Revenue,Expenses,Net\n");
			for (MonthlyReportRow row : reports) {
				csv.append(row.label()).append(",");
				csv.append(row.revenue()).append(",");
				csv.append(row.expenses()).append(",");
				csv.append(row.net()).append("\n");
			}
		}

		response.getWriter().write(csv.toString());
	}

	@SuppressWarnings("unused")
	private void writeExcelReport(
			HttpServletResponse response,
			String filename,
			Set<String> include,
			FinanceSnapshot snapshot,
			FinanceInsights insights,
			List<ExpenseRow> expenses,
			List<FinancialRecordRow> records,
			List<MonthlyReportRow> reports
	) throws IOException {
		response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
		response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");

		try (XSSFWorkbook workbook = new XSSFWorkbook()) {
			if (includeSection(include, "snapshot")) {
				Sheet sheet = workbook.createSheet("Snapshot");
				int rowIndex = 0;
				rowIndex = writeSheetHeader(sheet, rowIndex, "Snapshot");
				rowIndex = writeKeyValue(sheet, rowIndex, "Total Revenue", snapshot.totalRevenue());
				rowIndex = writeKeyValue(sheet, rowIndex, "Total Expenses", snapshot.totalExpenses());
				rowIndex = writeKeyValue(sheet, rowIndex, "Net Income", snapshot.netIncome());
				rowIndex = writeKeyValue(sheet, rowIndex, "Average Daily Expense", snapshot.avgDailyExpense());
				rowIndex = writeKeyValue(sheet, rowIndex, "Expense Entries", snapshot.expenseCount());
				rowIndex = writeKeyValue(sheet, rowIndex, "Financial Records", snapshot.recordCount());
				writeKeyValue(sheet, rowIndex, "Period", snapshot.periodLabel());
			}

			if (includeSection(include, "insights")) {
				Sheet sheet = workbook.createSheet("Insights");
				int rowIndex = 0;
				rowIndex = writeSheetHeader(sheet, rowIndex, "Insights (Last 7 Days)");
				rowIndex = writeKeyValue(sheet, rowIndex, "Revenue Change %", insights.revenueChangePercent());
				rowIndex = writeKeyValue(sheet, rowIndex, "Expense Change %", insights.expenseChangePercent());
				rowIndex = writeKeyValue(sheet, rowIndex, "Net Trend", insights.netTrendLabel());
				rowIndex = writeKeyValue(sheet, rowIndex, "Top Expense Category", insights.topExpenseType());
				rowIndex = writeKeyValue(sheet, rowIndex, "Top Expense Amount", insights.topExpenseAmount());
				rowIndex = writeKeyValue(sheet, rowIndex, "Best Revenue Day", insights.bestRevenueDay());
				writeKeyValue(sheet, rowIndex, "Best Revenue Amount", insights.bestRevenueAmount());
			}

			if (includeSection(include, "expenses")) {
				Sheet sheet = workbook.createSheet("Expenses");
				int rowIndex = 0;
				Row header = sheet.createRow(rowIndex++);
				header.createCell(0).setCellValue("Date");
				header.createCell(1).setCellValue("Type");
				header.createCell(2).setCellValue("Amount");
				header.createCell(3).setCellValue("Description");
				for (ExpenseRow row : expenses) {
					Row data = sheet.createRow(rowIndex++);
					data.createCell(0).setCellValue(row.date().toString());
					data.createCell(1).setCellValue(row.type());
					data.createCell(2).setCellValue(row.amount().doubleValue());
					data.createCell(3).setCellValue(row.description());
				}
			}

			if (includeSection(include, "records")) {
				Sheet sheet = workbook.createSheet("Records");
				int rowIndex = 0;
				Row header = sheet.createRow(rowIndex++);
				header.createCell(0).setCellValue("Date");
				header.createCell(1).setCellValue("Type");
				header.createCell(2).setCellValue("Amount");
				header.createCell(3).setCellValue("Notes");
				for (FinancialRecordRow row : records) {
					Row data = sheet.createRow(rowIndex++);
					data.createCell(0).setCellValue(row.date().toString());
					data.createCell(1).setCellValue(row.type());
					data.createCell(2).setCellValue(row.amount().doubleValue());
					data.createCell(3).setCellValue(row.notes());
				}
			}

			if (includeSection(include, "reports")) {
				Sheet sheet = workbook.createSheet("Monthly Reports");
				int rowIndex = 0;
				Row header = sheet.createRow(rowIndex++);
				header.createCell(0).setCellValue("Month");
				header.createCell(1).setCellValue("Revenue");
				header.createCell(2).setCellValue("Expenses");
				header.createCell(3).setCellValue("Net");
				for (MonthlyReportRow row : reports) {
					Row data = sheet.createRow(rowIndex++);
					data.createCell(0).setCellValue(row.label());
					data.createCell(1).setCellValue(row.revenue().doubleValue());
					data.createCell(2).setCellValue(row.expenses().doubleValue());
					data.createCell(3).setCellValue(row.net().doubleValue());
				}
			}

			workbook.write(response.getOutputStream());
		}
	}

	@SuppressWarnings("unused")
	private void writePdfReport(
			HttpServletResponse response,
			String filename,
			Set<String> include,
			FinanceSnapshot snapshot,
			FinanceInsights insights,
			List<ExpenseRow> expenses,
			List<FinancialRecordRow> records,
			List<MonthlyReportRow> reports
	) throws IOException {
		response.setContentType("application/pdf");
		response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");

		try (Document document = new Document()) {
			PdfWriter.getInstance(document, response.getOutputStream());
			document.open();
			document.add(new Paragraph("Finance and Accounting Report"));
			document.add(new Paragraph("Generated: " + LocalDate.now()));
			document.add(new Paragraph(" "));

			if (includeSection(include, "snapshot")) {
				document.add(new Paragraph("Snapshot"));
				PdfPTable table = new PdfPTable(2);
				table.addCell("Total Revenue");
				table.addCell(String.valueOf(snapshot.totalRevenue()));
				table.addCell("Total Expenses");
				table.addCell(String.valueOf(snapshot.totalExpenses()));
				table.addCell("Net Income");
				table.addCell(String.valueOf(snapshot.netIncome()));
				table.addCell("Average Daily Expense");
				table.addCell(String.valueOf(snapshot.avgDailyExpense()));
				table.addCell("Expense Entries");
				table.addCell(String.valueOf(snapshot.expenseCount()));
				table.addCell("Financial Records");
				table.addCell(String.valueOf(snapshot.recordCount()));
				table.addCell("Period");
				table.addCell(snapshot.periodLabel());
				document.add(table);
				document.add(new Paragraph(" "));
			}

			if (includeSection(include, "insights")) {
				document.add(new Paragraph("Insights (Last 7 Days)"));
				PdfPTable table = new PdfPTable(2);
				table.addCell("Revenue Change %");
				table.addCell(String.valueOf(insights.revenueChangePercent()));
				table.addCell("Expense Change %");
				table.addCell(String.valueOf(insights.expenseChangePercent()));
				table.addCell("Net Trend");
				table.addCell(insights.netTrendLabel());
				table.addCell("Top Expense Category");
				table.addCell(insights.topExpenseType());
				table.addCell("Top Expense Amount");
				table.addCell(String.valueOf(insights.topExpenseAmount()));
				table.addCell("Best Revenue Day");
				table.addCell(insights.bestRevenueDay());
				table.addCell("Best Revenue Amount");
				table.addCell(String.valueOf(insights.bestRevenueAmount()));
				document.add(table);
				document.add(new Paragraph(" "));
			}

			if (includeSection(include, "expenses")) {
				document.add(new Paragraph("Expenses"));
				PdfPTable table = new PdfPTable(4);
				table.addCell("Date");
				table.addCell("Type");
				table.addCell("Amount");
				table.addCell("Description");
				for (ExpenseRow row : expenses) {
					table.addCell(row.date().toString());
					table.addCell(row.type());
					table.addCell(String.valueOf(row.amount()));
					table.addCell(row.description());
				}
				document.add(table);
				document.add(new Paragraph(" "));
			}

			if (includeSection(include, "records")) {
				document.add(new Paragraph("Financial Records"));
				PdfPTable table = new PdfPTable(4);
				table.addCell("Date");
				table.addCell("Type");
				table.addCell("Amount");
				table.addCell("Notes");
				for (FinancialRecordRow row : records) {
					table.addCell(row.date().toString());
					table.addCell(row.type());
					table.addCell(String.valueOf(row.amount()));
					table.addCell(row.notes());
				}
				document.add(table);
				document.add(new Paragraph(" "));
			}

			if (includeSection(include, "reports")) {
				document.add(new Paragraph("Monthly Reports"));
				PdfPTable table = new PdfPTable(4);
				table.addCell("Month");
				table.addCell("Revenue");
				table.addCell("Expenses");
				table.addCell("Net");
				for (MonthlyReportRow row : reports) {
					table.addCell(row.label());
					table.addCell(String.valueOf(row.revenue()));
					table.addCell(String.valueOf(row.expenses()));
					table.addCell(String.valueOf(row.net()));
				}
				document.add(table);
			}
		} catch (DocumentException ex) {
			throw new IOException("Failed to generate PDF report", ex);
		}
	}

	@SuppressWarnings("unused")
	private static int writeSheetHeader(Sheet sheet, int rowIndex, String title) {
		Row row = sheet.createRow(rowIndex++);
		row.createCell(0).setCellValue(title);
		return rowIndex;
	}

	@SuppressWarnings("unused")
	private static int writeKeyValue(Sheet sheet, int rowIndex, String key, Object value) {
		Row row = sheet.createRow(rowIndex++);
		row.createCell(0).setCellValue(key);
		row.createCell(1).setCellValue(value == null ? "" : String.valueOf(value));
		return rowIndex;
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

	public record FinanceInsights(
			double revenueChangePercent,
			double expenseChangePercent,
			String netTrendLabel,
			String topExpenseType,
			BigDecimal topExpenseAmount,
			String bestRevenueDay,
			BigDecimal bestRevenueAmount
	) {
		public static FinanceInsights empty() {
			return new FinanceInsights(0, 0, "Stable", "None", BigDecimal.ZERO, "N/A", BigDecimal.ZERO);
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

	private record DailyRevenueSummary(String label, BigDecimal total) { }

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
