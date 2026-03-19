package com.example.demo.web;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.List;
import jakarta.servlet.http.HttpSession;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardController {

	private final JdbcTemplate jdbcTemplate;

	public DashboardController(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@GetMapping("/")
	public String dashboard(Model model, HttpSession session) {
		UserProfile userProfile = SessionUtil.getUser(session);
		if (userProfile == null) {
			return "redirect:/login";
		}
		model.addAttribute("siteTitle", "PureWell Refilling Station ERP");
		model.addAttribute("today", LocalDate.now());
		model.addAttribute("userProfile", userProfile);
		try {
			DashboardStats stats = loadStats();
			model.addAttribute("stats", stats);
			model.addAttribute("latestDeliveries", loadLatestDeliveries());
			model.addAttribute("weekdayBars", loadWeekdayBars());
			loadFleetTrendSeries(model);
			loadRevenueSeries(model);
			model.addAttribute("dbAvailable", true);
		} catch (DataAccessException ex) {
			model.addAttribute("stats", DashboardStats.empty());
			model.addAttribute("latestDeliveries", List.of());
			model.addAttribute("weekdayBars", defaultWeekdayBars());
			loadFleetTrendSeriesFallback(model);
			loadRevenueSeriesFallback(model);
			model.addAttribute("dbAvailable", false);
		}
		return "dashboard";
	}

	public record ModuleCard(String title, String description, String status) { }

	private DashboardStats loadStats() {
		Integer activeVehicles = jdbcTemplate.queryForObject(
				"select count(*) from vehicles where status = 'Active'", Integer.class);
		Double distanceDriven = jdbcTemplate.queryForObject(
				"select coalesce(sum(distance_driven),0) from vehicle_tracking where tracking_date >= now() - interval '7 days'",
				Double.class);
		Double emptyMiles = jdbcTemplate.queryForObject(
				"select coalesce(sum(empty_miles),0) from vehicle_tracking where tracking_date >= now() - interval '7 days'",
				Double.class);
		Double serviceTime = jdbcTemplate.queryForObject(
				"select coalesce(sum(service_time),0) from vehicle_tracking where tracking_date >= now() - interval '7 days'",
				Double.class);
		Integer serviceNeeded = jdbcTemplate.queryForObject(
				"select count(*) from vehicles where status = 'Under Service'", Integer.class);
		Integer totalVehicles = jdbcTemplate.queryForObject(
				"select count(*) from vehicles", Integer.class);
		double servicePercent = totalVehicles == null || totalVehicles == 0 ? 0
				: (serviceNeeded == null ? 0 : (serviceNeeded * 100.0 / totalVehicles));

		return new DashboardStats(
				valueOrZero(activeVehicles),
				round1(valueOrZero(distanceDriven)),
				round1(valueOrZero(emptyMiles)),
				round1(valueOrZero(serviceTime)),
				round1(servicePercent),
				valueOrZero(serviceNeeded)
		);
	}

	private List<DeliveryRow> loadLatestDeliveries() {
		return jdbcTemplate.query(
				"""
				select d.id, c.name, s.total, d.status
				from deliveries d
				join sales s on d.sales_id = s.id
				join customers c on s.customer_id = c.id
				order by d.delivery_date desc, d.id desc
				limit 4
				""",
				(rs, rowNum) -> new DeliveryRow(
						rs.getInt("id"),
						rs.getString("name"),
						rs.getBigDecimal("total"),
						rs.getString("status")
				)
		);
	}

	private List<WeekdayBar> loadWeekdayBars() {
		List<WeekdayCount> raw = jdbcTemplate.query(
				"""
				select to_char(sale_date, 'Dy') as day_label, count(*) as total
				from sales
				where sale_date >= now() - interval '7 days'
				group by to_char(sale_date, 'Dy')
				""",
				(rs, rowNum) -> new WeekdayCount(rs.getString("day_label").trim(), rs.getInt("total"))
		);

		List<String> order = List.of("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun");
		int max = raw.stream().mapToInt(WeekdayCount::count).max().orElse(1);

		return order.stream()
				.map(label -> {
					int count = raw.stream()
							.filter(item -> item.label().equalsIgnoreCase(label))
							.mapToInt(WeekdayCount::count)
							.findFirst()
							.orElse(0);
					int percent = (int) Math.round((count * 100.0) / max);
					return new WeekdayBar(label, count, Math.max(percent, 10));
				})
				.toList();
	}

	private static int valueOrZero(Integer value) {
		return value == null ? 0 : value;
	}

	private static double valueOrZero(Double value) {
		return value == null ? 0 : value;
	}

	private static double round1(double value) {
		return Math.round(value * 10.0) / 10.0;
	}

	public record DashboardStats(
			int activeVehicles,
			double distanceDriven,
			double emptyMiles,
			double serviceTime,
			double servicePercent,
			int serviceNeeded
	) {
		public static DashboardStats empty() {
			return new DashboardStats(0, 0, 0, 0, 0, 0);
		}
	}

	public record DeliveryRow(int id, String customer, java.math.BigDecimal total, String status) { }

	private record WeekdayCount(String label, int count) { }

	public record WeekdayBar(String label, int count, int percent) { }

	private List<WeekdayBar> defaultWeekdayBars() {
		return List.of(
				new WeekdayBar("Mon", 0, 10),
				new WeekdayBar("Tue", 0, 10),
				new WeekdayBar("Wed", 0, 10),
				new WeekdayBar("Thu", 0, 10),
				new WeekdayBar("Fri", 0, 10),
				new WeekdayBar("Sat", 0, 10),
				new WeekdayBar("Sun", 0, 10)
		);
	}

	private void loadFleetTrendSeries(Model model) {
		List<DailyFleetRow> rows = jdbcTemplate.query(
				"""
				select date_trunc('day', tracking_date) as day,
				       coalesce(sum(distance_driven),0) as distance,
				       coalesce(sum(empty_miles),0) as empty_miles
				from vehicle_tracking
				where tracking_date >= now() - interval '7 days'
				group by date_trunc('day', tracking_date)
				order by day
				""",
				(rs, rowNum) -> new DailyFleetRow(
						rs.getTimestamp("day").toInstant().atZone(ZoneId.systemDefault()).toLocalDate(),
						rs.getDouble("distance"),
						rs.getDouble("empty_miles")
				)
		);

		List<LocalDate> days = LocalDate.now().minusDays(6).datesUntil(LocalDate.now().plusDays(1)).toList();
		List<String> labels = days.stream()
				.map(day -> day.getMonthValue() + "/" + day.getDayOfMonth())
				.toList();
		List<Double> distanceSeries = days.stream()
				.map(day -> rows.stream()
						.filter(row -> row.day().equals(day))
						.findFirst()
						.map(row -> round1(row.distance()))
						.orElse(0.0))
				.toList();
		List<Double> emptySeries = days.stream()
				.map(day -> rows.stream()
						.filter(row -> row.day().equals(day))
						.findFirst()
						.map(row -> round1(row.emptyMiles()))
						.orElse(0.0))
				.toList();

		model.addAttribute("fleetLabels", labels);
		model.addAttribute("fleetDistance", distanceSeries);
		model.addAttribute("fleetEmpty", emptySeries);
	}

	private void loadFleetTrendSeriesFallback(Model model) {
		model.addAttribute("fleetLabels", List.of("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"));
		model.addAttribute("fleetDistance", List.of(0, 0, 0, 0, 0, 0, 0));
		model.addAttribute("fleetEmpty", List.of(0, 0, 0, 0, 0, 0, 0));
	}

	private void loadRevenueSeries(Model model) {
		List<DailyRevenueRow> rows = jdbcTemplate.query(
				"""
				select date_trunc('day', sale_date) as day,
				       coalesce(sum(total),0) as revenue
				from sales
				where sale_date >= now() - interval '14 days'
				group by date_trunc('day', sale_date)
				order by day
				""",
				(rs, rowNum) -> new DailyRevenueRow(
						rs.getTimestamp("day").toInstant().atZone(ZoneId.systemDefault()).toLocalDate(),
						rs.getDouble("revenue")
				)
		);

		List<LocalDate> days = LocalDate.now().minusDays(13).datesUntil(LocalDate.now().plusDays(1)).toList();
		List<String> labels = days.stream()
				.map(day -> day.getMonthValue() + "/" + day.getDayOfMonth())
				.toList();
		List<Double> revenueSeries = days.stream()
				.map(day -> rows.stream()
						.filter(row -> row.day().equals(day))
						.findFirst()
						.map(row -> round1(row.revenue()))
						.orElse(0.0))
				.toList();

		model.addAttribute("revenueLabels", labels);
		model.addAttribute("revenueSeries", revenueSeries);
	}

	private void loadRevenueSeriesFallback(Model model) {
		model.addAttribute("revenueLabels", List.of("Day 1", "Day 2", "Day 3", "Day 4", "Day 5", "Day 6", "Day 7"));
		model.addAttribute("revenueSeries", List.of(0, 0, 0, 0, 0, 0, 0));
	}

	private record DailyFleetRow(LocalDate day, double distance, double emptyMiles) { }

	private record DailyRevenueRow(LocalDate day, double revenue) { }
}
