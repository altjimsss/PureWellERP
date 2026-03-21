package com.example.demo.web;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ArrayList;
import jakarta.servlet.http.HttpSession;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class DashboardController {

	private final JdbcTemplate jdbcTemplate;
	private final AuditLogService auditLogService;

	public DashboardController(JdbcTemplate jdbcTemplate, AuditLogService auditLogService) {
		this.jdbcTemplate = jdbcTemplate;
		this.auditLogService = auditLogService;
	}

	@GetMapping("/")
	public String dashboard(
			Model model,
			HttpSession session,
			@RequestParam(name = "q", required = false) String query,
			@RequestParam(name = "scope", required = false) String scope,
			@RequestParam(name = "dashboardDate", required = false)
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dashboardDate
	) {
		UserProfile userProfile = SessionUtil.getUser(session);
		if (userProfile == null) {
			return "redirect:/login";
		}
		LocalDate filterDate = dashboardDate != null ? dashboardDate : LocalDate.now();
		model.addAttribute("siteTitle", "PureWell Refilling Station ERP");
		model.addAttribute("today", filterDate);
		model.addAttribute("userProfile", userProfile);
		model.addAttribute("isAdmin", isAdmin(userProfile));
		model.addAttribute("searchQuery", query == null ? "" : query.trim());
		model.addAttribute("searchScope", scope == null ? "all" : scope);
		try {
			DashboardStats stats = loadStats(filterDate);
			model.addAttribute("stats", stats);
			model.addAttribute("kpiCards", buildKpis(stats, userProfile));
			model.addAttribute("latestDeliveries", loadLatestDeliveries(filterDate));
			model.addAttribute("weekdayBars", loadWeekdayBars(filterDate));
			loadFleetTrendSeries(model, filterDate);
			loadRevenueSeries(model, filterDate);
			model.addAttribute("alerts", loadAlerts(filterDate));
			model.addAttribute("tasks", loadTasks(userProfile));
			model.addAttribute("notificationRules", loadNotificationRules(userProfile));
			model.addAttribute("auditLogs", loadRecentAuditLogs(userProfile));
			if (query != null && !query.isBlank()) {
				model.addAttribute("searchResults", loadSearchResults(query.trim(), scope));
			} else {
				model.addAttribute("searchResults", List.of());
			}
			model.addAttribute("quickActions", quickActions());
			model.addAttribute("dbAvailable", true);
		} catch (DataAccessException ex) {
			model.addAttribute("stats", DashboardStats.empty());
			model.addAttribute("kpiCards", buildKpis(DashboardStats.empty(), userProfile));
			model.addAttribute("latestDeliveries", List.of());
			model.addAttribute("weekdayBars", defaultWeekdayBars());
			loadFleetTrendSeriesFallback(model);
			loadRevenueSeriesFallback(model);
			model.addAttribute("alerts", List.of());
			model.addAttribute("tasks", List.of());
			model.addAttribute("notificationRules", List.of());
			model.addAttribute("auditLogs", List.of());
			model.addAttribute("searchResults", List.of());
			model.addAttribute("quickActions", quickActions());
			model.addAttribute("dbAvailable", false);
		}
		return "dashboard";
	}

	@PostMapping("/dashboard/tasks")
	public String createTask(
			@RequestParam("title") String title,
			@RequestParam(name = "dueDate", required = false)
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueDate,
			@RequestParam(name = "priority", required = false) String priority,
			HttpSession session
	) {
		UserProfile userProfile = SessionUtil.getUser(session);
		if (userProfile == null) {
			return "redirect:/login";
		}
		String sanitizedPriority = priority == null || priority.isBlank() ? "Normal" : priority;
		jdbcTemplate.update(
				"""
				insert into tasks(title, status, due_date, priority, created_at, created_by)
				values(?, 'Open', ?, ?, now(), ?)
				""",
				title, dueDate, sanitizedPriority, userProfile.employeeId()
		);
		auditLogService.log(
				"CREATE",
				"Task",
				null,
				"Created task: " + title,
				userProfile
		);
		return "redirect:/";
	}

	@PostMapping("/dashboard/notifications")
	public String createNotificationRule(
			@RequestParam("eventType") String eventType,
			@RequestParam("channel") String channel,
			@RequestParam("target") String target,
			HttpSession session
	) {
		UserProfile userProfile = SessionUtil.getUser(session);
		if (userProfile == null) {
			return "redirect:/login";
		}
		jdbcTemplate.update(
				"""
				insert into notification_rules(event_type, channel, target, is_active, created_at, created_by)
				values(?, ?, ?, true, now(), ?)
				""",
				eventType, channel, target, userProfile.employeeId()
		);
		auditLogService.log(
				"CREATE",
				"NotificationRule",
				null,
				"Rule: " + eventType + " via " + channel,
				userProfile
		);
		return "redirect:/";
	}

	public record ModuleCard(String title, String description, String status) { }

	private DashboardStats loadStats(LocalDate endDate) {
		Integer activeVehicles = jdbcTemplate.queryForObject(
				"select count(*) from vehicles where status = 'Active'", Integer.class);
		Double distanceDriven = jdbcTemplate.queryForObject(
				"""
				select coalesce(sum(distance_driven),0)
				from vehicle_tracking
				where tracking_date >= ?::date - interval '7 days'
				  and tracking_date < ?::date + interval '1 day'
				""",
				Double.class,
				endDate, endDate
		);
		Double emptyMiles = jdbcTemplate.queryForObject(
				"""
				select coalesce(sum(empty_miles),0)
				from vehicle_tracking
				where tracking_date >= ?::date - interval '7 days'
				  and tracking_date < ?::date + interval '1 day'
				""",
				Double.class,
				endDate, endDate
		);
		Double serviceTime = jdbcTemplate.queryForObject(
				"""
				select coalesce(sum(service_time),0)
				from vehicle_tracking
				where tracking_date >= ?::date - interval '7 days'
				  and tracking_date < ?::date + interval '1 day'
				""",
				Double.class,
				endDate, endDate
		);
		Integer serviceNeeded = jdbcTemplate.queryForObject(
				"select count(*) from vehicles where status = 'Under Service'", Integer.class);
		Integer totalVehicles = jdbcTemplate.queryForObject(
				"select count(*) from vehicles", Integer.class);
		return buildStats(activeVehicles, distanceDriven, emptyMiles, serviceTime, serviceNeeded, totalVehicles);
	}

	private DashboardStats buildStats(
			Integer activeVehicles,
			Double distanceDriven,
			Double emptyMiles,
			Double serviceTime,
			Integer serviceNeeded,
			Integer totalVehicles
	) {
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

	private List<KpiCard> buildKpis(DashboardStats stats, UserProfile profile) {
		List<KpiCard> cards = new ArrayList<>();
		cards.add(new KpiCard("Vehicles on Track", stats.activeVehicles() + " units", "Active fleet status", "down"));
		cards.add(new KpiCard("Distance Driven", stats.distanceDriven() + " km", "Last 7 days", "up"));
		cards.add(new KpiCard("Empty Miles", stats.emptyMiles() + " km", "Last 7 days", "down"));
		cards.add(new KpiCard("Service Time", stats.serviceTime() + " hrs", "Last 7 days", "up"));
		if (isAdmin(profile)) {
			cards.add(new KpiCard("Service Needed", String.valueOf(stats.serviceNeeded()), "Vehicles awaiting service", "down"));
			cards.add(new KpiCard("Service Load", round1(stats.servicePercent()) + "%", "Of total fleet", "up"));
		}
		return cards;
	}

	public record KpiCard(String title, String value, String note, String trend) { }

	private boolean isAdmin(UserProfile userProfile) {
		return userProfile != null && "Admin".equalsIgnoreCase(userProfile.role());
	}

	private List<DeliveryRow> loadLatestDeliveries(LocalDate endDate) {
		return jdbcTemplate.query(
				"""
				select d.id, c.name, s.total, d.status
				from deliveries d
				join sales s on d.sales_id = s.id
				join customers c on s.customer_id = c.id
				where d.delivery_date <= ?::date
				order by d.delivery_date desc, d.id desc
				limit 4
				""",
				(rs, rowNum) -> new DeliveryRow(
						rs.getInt("id"),
						rs.getString("name"),
						rs.getBigDecimal("total"),
						rs.getString("status")
				),
				endDate
		);
	}

	private List<WeekdayBar> loadWeekdayBars(LocalDate endDate) {
		List<WeekdayCount> raw = jdbcTemplate.query(
				"""
				select to_char(sale_date, 'Dy') as day_label, count(*) as total
				from sales
				where sale_date >= ?::date - interval '7 days'
				  and sale_date < ?::date + interval '1 day'
				group by to_char(sale_date, 'Dy')
				""",
				(rs, rowNum) -> new WeekdayCount(rs.getString("day_label").trim(), rs.getInt("total")),
				endDate, endDate
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

	private List<SearchResult> loadSearchResults(String query, String scope) {
		String needle = "%" + query.toLowerCase() + "%";
		List<SearchResult> results = new ArrayList<>();
		if (scope == null || "all".equalsIgnoreCase(scope) || "customers".equalsIgnoreCase(scope)) {
			results.addAll(jdbcTemplate.query(
					"""
					select id, name, contact, address
					from customers
					where lower(name) like ? or lower(contact) like ? or lower(address) like ?
					order by id desc
					limit 5
					""",
					(rs, rowNum) -> new SearchResult(
							"Customer",
							rs.getString("name"),
							rs.getString("contact"),
							"/modules/sales"
					),
					needle, needle, needle
			));
		}
		if (scope == null || "all".equalsIgnoreCase(scope) || "orders".equalsIgnoreCase(scope)) {
			results.addAll(jdbcTemplate.query(
					"""
					select o.id, c.name, o.status, o.order_date
					from orders o
					join customers c on o.customer_id = c.id
					where cast(o.id as text) like ? or lower(c.name) like ?
					order by o.order_date desc
					limit 5
					""",
					(rs, rowNum) -> new SearchResult(
							"Order",
							"Order #" + rs.getInt("id"),
							rs.getString("name") + " • " + rs.getString("status"),
							"/modules/sales"
					),
					needle, needle
			));
		}
		if (scope == null || "all".equalsIgnoreCase(scope) || "inventory".equalsIgnoreCase(scope)) {
			results.addAll(jdbcTemplate.query(
					"""
					select id, name, type, stock
					from products
					where lower(name) like ? or lower(type) like ?
					order by id desc
					limit 5
					""",
					(rs, rowNum) -> new SearchResult(
							"Inventory",
							rs.getString("name"),
							rs.getString("type") + " • Stock " + rs.getInt("stock"),
							"/modules/procurement"
					),
					needle, needle
			));
		}
		if (scope == null || "all".equalsIgnoreCase(scope) || "finance".equalsIgnoreCase(scope)) {
			results.addAll(jdbcTemplate.query(
					"""
					select id, record_type, amount, date
					from financial_records
					where lower(record_type) like ? or cast(amount as text) like ?
					order by date desc
					limit 5
					""",
					(rs, rowNum) -> new SearchResult(
							"Finance",
							rs.getString("record_type") + " • " + rs.getBigDecimal("amount"),
							String.valueOf(rs.getDate("date")),
							"/modules/finance"
					),
					needle, needle
			));
		}
		return results;
	}

	public record SearchResult(String type, String title, String subtitle, String href) { }

	private List<AlertItem> loadAlerts(LocalDate endDate) {
		List<AlertItem> alerts = new ArrayList<>();
		alerts.addAll(jdbcTemplate.query(
				"select id, name, stock from products where stock < 50 order by stock asc limit 3",
				(rs, rowNum) -> new AlertItem(
						"Low stock",
						rs.getString("name") + " • " + rs.getInt("stock") + " left",
						"/modules/procurement",
						"warn"
				)
		));
		alerts.addAll(jdbcTemplate.query(
				"""
				select d.id, c.name, d.delivery_date
				from deliveries d
				join sales s on d.sales_id = s.id
				join customers c on s.customer_id = c.id
				where d.status <> 'Delivered'
				  and d.delivery_date < ?::date
				order by d.delivery_date asc
				limit 3
				""",
				(rs, rowNum) -> new AlertItem(
						"Overdue delivery",
						rs.getString("name") + " • " + rs.getDate("delivery_date"),
						"/modules/sales",
						"danger"
				),
				endDate
		));
		return alerts;
	}

	public record AlertItem(String title, String description, String href, String tone) { }

	private List<TaskItem> loadTasks(UserProfile profile) {
		return jdbcTemplate.query(
				"""
				select id, title, status, due_date, priority
				from tasks
				where created_by = ?
				order by created_at desc
				limit 6
				""",
				(rs, rowNum) -> new TaskItem(
						rs.getInt("id"),
						rs.getString("title"),
						rs.getString("status"),
						rs.getDate("due_date") == null ? null : rs.getDate("due_date").toLocalDate(),
						rs.getString("priority")
				),
				profile.employeeId()
		);
	}

	public record TaskItem(int id, String title, String status, LocalDate dueDate, String priority) { }

	private List<NotificationRule> loadNotificationRules(UserProfile profile) {
		return jdbcTemplate.query(
				"""
				select id, event_type, channel, target, is_active, created_at
				from notification_rules
				where created_by = ?
				order by created_at desc
				limit 6
				""",
				(rs, rowNum) -> new NotificationRule(
						rs.getInt("id"),
						rs.getString("event_type"),
						rs.getString("channel"),
						rs.getString("target"),
						rs.getBoolean("is_active"),
						rs.getTimestamp("created_at").toLocalDateTime()
				),
				profile.employeeId()
		);
	}

	public record NotificationRule(int id, String eventType, String channel, String target, boolean active, LocalDateTime createdAt) { }

	private List<AuditLogEntry> loadRecentAuditLogs(UserProfile profile) {
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd, HH:mm");
		return jdbcTemplate.query(
				"""
				select action, entity, entity_id, details, created_at, actor_name
				from audit_logs
				where actor_id = ?
				order by created_at desc
				limit 8
				""",
				(rs, rowNum) -> new AuditLogEntry(
						rs.getString("action"),
						rs.getString("entity"),
						rs.getString("entity_id"),
						rs.getString("details"),
						rs.getTimestamp("created_at").toLocalDateTime(),
						rs.getString("actor_name"),
						rs.getTimestamp("created_at").toLocalDateTime().format(formatter)
				),
				profile.employeeId()
		);
	}

	public record AuditLogEntry(
			String action,
			String entity,
			String entityId,
			String details,
			LocalDateTime createdAt,
			String actorName,
			String displayTime
	) { }

	private List<QuickAction> quickActions() {
		return List.of(
				new QuickAction("Add Customer", "/modules/sales", "sales"),
				new QuickAction("Record Sale", "/modules/sales", "sales"),
				new QuickAction("Add Expense", "/modules/finance", "finance"),
				new QuickAction("New Purchase Order", "/modules/procurement", "procurement")
		);
	}

	public record QuickAction(String label, String href, String tone) { }

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

	private void loadFleetTrendSeries(Model model, LocalDate endDate) {
		List<DailyFleetRow> rows = jdbcTemplate.query(
				"""
				select date_trunc('day', tracking_date) as day,
				       coalesce(sum(distance_driven),0) as distance,
				       coalesce(sum(empty_miles),0) as empty_miles
				from vehicle_tracking
				where tracking_date >= ?::date - interval '7 days'
				  and tracking_date < ?::date + interval '1 day'
				group by date_trunc('day', tracking_date)
				order by day
				""",
				(rs, rowNum) -> new DailyFleetRow(
						rs.getTimestamp("day").toInstant().atZone(ZoneId.systemDefault()).toLocalDate(),
						rs.getDouble("distance"),
						rs.getDouble("empty_miles")
				),
				endDate, endDate
		);

		List<LocalDate> days = endDate.minusDays(6).datesUntil(endDate.plusDays(1)).toList();
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

	private void loadRevenueSeries(Model model, LocalDate endDate) {
		List<DailyRevenueRow> rows = jdbcTemplate.query(
				"""
				select date_trunc('day', sale_date) as day,
				       coalesce(sum(total),0) as revenue
				from sales
				where sale_date >= ?::date - interval '14 days'
				  and sale_date < ?::date + interval '1 day'
				group by date_trunc('day', sale_date)
				order by day
				""",
				(rs, rowNum) -> new DailyRevenueRow(
						rs.getTimestamp("day").toInstant().atZone(ZoneId.systemDefault()).toLocalDate(),
						rs.getDouble("revenue")
				),
				endDate, endDate
		);

		List<LocalDate> days = endDate.minusDays(13).datesUntil(endDate.plusDays(1)).toList();
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

	record DailyFleetRow(LocalDate day, double distance, double emptyMiles) { }

	record DailyRevenueRow(LocalDate day, double revenue) { }
}
