package com.example.demo.web;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class HrmController {

	private final JdbcTemplate jdbcTemplate;

	public HrmController(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@GetMapping("/modules/hrm")
	public String hrmModule(Model model) {
		model.addAttribute("siteTitle", "Human Resource Management Module");
		model.addAttribute("moduleName", "Human Resource Management");
		try {
			LocalDate attendanceDate = loadAttendanceDate();
			model.addAttribute("attendanceDate", attendanceDate);
			model.addAttribute("snapshot", loadSnapshot(attendanceDate));
			model.addAttribute("recentEmployees", loadRecentEmployees());
			model.addAttribute("departments", loadDepartments());
			model.addAttribute("positions", loadPositions());
			model.addAttribute("attendanceToday", loadAttendanceToday(attendanceDate));
			model.addAttribute("attendanceSummary", loadAttendanceSummary(attendanceDate));
			model.addAttribute("payrollBasics", loadPayrollBasics());
			model.addAttribute("payrollFeatures", payrollFeatures());
			model.addAttribute("dbAvailable", true);
		} catch (DataAccessException ex) {
			model.addAttribute("attendanceDate", LocalDate.now());
			model.addAttribute("snapshot", HrmSnapshot.empty());
			model.addAttribute("recentEmployees", List.of());
			model.addAttribute("departments", List.of());
			model.addAttribute("positions", List.of());
			model.addAttribute("attendanceToday", List.of());
			model.addAttribute("attendanceSummary", AttendanceSummary.empty());
			model.addAttribute("payrollBasics", PayrollBasics.empty());
			model.addAttribute("payrollFeatures", payrollFeatures());
			model.addAttribute("dbAvailable", false);
		}
		return "hrm-module";
	}

	@PostMapping("/modules/hrm/employees")
	public String addEmployee(
			@RequestParam("firstName") String firstName,
			@RequestParam("lastName") String lastName,
			@RequestParam("position") String position,
			@RequestParam("department") String department,
			@RequestParam("hireDate") String hireDate,
			@RequestParam("salary") Double salary
	) {
		jdbcTemplate.update(
				"""
				insert into employees (first_name, last_name, position, department, hire_date, salary)
				values (?, ?, ?, ?, ?, ?)
				""",
				firstName,
				lastName,
				position,
				department,
				LocalDate.parse(hireDate),
				salary
		);
		return "redirect:/modules/hrm";
	}

	@PostMapping("/modules/hrm/employees/delete")
	public String removeEmployee(@RequestParam("employeeId") Integer employeeId) {
		jdbcTemplate.update("delete from attendance where employee_id = ?", employeeId);
		jdbcTemplate.update("delete from attendance_summary where employee_id = ?", employeeId);
		jdbcTemplate.update("delete from employees where id = ?", employeeId);
		return "redirect:/modules/hrm";
	}

	private List<Map<String, Object>> payrollFeatures() {
		return List.of(
				Map.of(
						"title", "Salary Calculation",
						"items", List.of(
								"Compute gross pay (basic salary + allowances + overtime)",
								"Deduct taxes, social security, and other contributions"
						)
				),
				Map.of(
						"title", "Payroll Processing",
						"items", List.of(
								"Generate paychecks or direct deposits",
								"Ensure employees are paid on the scheduled date"
						)
				),
				Map.of(
						"title", "Record Keeping",
						"items", List.of(
								"Maintain payroll records for each employee",
								"Keep track of deductions, bonuses, and tax filings"
						)
				),
				Map.of(
						"title", "Compliance & Reporting",
						"items", List.of(
								"Ensure payroll complies with labor laws and tax regulations",
								"Prepare reports for government agencies"
						)
				),
				Map.of(
						"title", "Benefits & Deductions",
						"items", List.of(
								"Handle employee benefits (insurance, retirement contributions)",
								"Manage deductions for loans, advances, or penalties"
						)
				)
		);
	}

	private LocalDate loadAttendanceDate() {
		LocalDate latest = jdbcTemplate.query(
				"select max(date) as latest from attendance",
				(rs, rowNum) -> {
					Date date = rs.getDate("latest");
					return date == null ? null : date.toLocalDate();
				}
		).stream().findFirst().orElse(null);
		return latest == null ? LocalDate.now() : latest;
	}

	private HrmSnapshot loadSnapshot(LocalDate attendanceDate) {
		Integer totalEmployees = jdbcTemplate.queryForObject(
				"select count(*) from employees", Integer.class);
		Integer departmentCount = jdbcTemplate.queryForObject(
				"select count(distinct department) from employees", Integer.class);
		Integer newHires = jdbcTemplate.queryForObject(
				"select count(*) from employees where hire_date >= current_date - interval '30 days'", Integer.class);
		Integer presentToday = jdbcTemplate.queryForObject(
				"select count(*) from attendance where date = ? and status = 'Present'",
				Integer.class,
				Date.valueOf(attendanceDate));
		Integer absentToday = jdbcTemplate.queryForObject(
				"select count(*) from attendance where date = ? and status = 'Absent'",
				Integer.class,
				Date.valueOf(attendanceDate));

		int present = valueOrZero(presentToday);
		int absent = valueOrZero(absentToday);
		int totalAttendance = present + absent;
		int attendanceRate = totalAttendance == 0 ? 0 : (int) Math.round((present * 100.0) / totalAttendance);

		return new HrmSnapshot(
				valueOrZero(totalEmployees),
				valueOrZero(departmentCount),
				valueOrZero(newHires),
				present,
				absent,
				attendanceRate,
				"As of " + attendanceDate
		);
	}

	private List<EmployeeRow> loadRecentEmployees() {
		return jdbcTemplate.query(
				"""
				select id, first_name, last_name, position, department, hire_date, salary
				from employees
				order by hire_date desc, id desc
				""",
				(rs, rowNum) -> new EmployeeRow(
						rs.getInt("id"),
						formatEmployeeName(
								rs.getInt("id"),
								rs.getString("first_name"),
								rs.getString("last_name")
						),
						rs.getString("position"),
						rs.getString("department"),
						rs.getDate("hire_date").toLocalDate(),
						rs.getBigDecimal("salary")
				)
		);
	}

	private List<String> loadDepartments() {
		return jdbcTemplate.query(
				"select distinct department from employees order by department",
				(rs, rowNum) -> rs.getString("department")
		).stream().filter(value -> value != null && !value.isBlank()).toList();
	}

	private List<String> loadPositions() {
		return jdbcTemplate.query(
				"select distinct position from employees order by position",
				(rs, rowNum) -> rs.getString("position")
		).stream().filter(value -> value != null && !value.isBlank()).toList();
	}

	private List<AttendanceRow> loadAttendanceToday(LocalDate attendanceDate) {
		return jdbcTemplate.query(
				"""
				select a.id, a.date, e.first_name, e.last_name, e.department, a.status
				from attendance a
				join employees e on a.employee_id = e.id
				where a.date = ?
				order by e.last_name asc, e.first_name asc
				limit 8
				""",
				(rs, rowNum) -> new AttendanceRow(
						rs.getInt("id"),
						rs.getDate("date").toLocalDate(),
						formatEmployeeName(
								rs.getInt("id"),
								rs.getString("first_name"),
								rs.getString("last_name")
						),
						rs.getString("department"),
						rs.getString("status")
				),
				Date.valueOf(attendanceDate)
		);
	}

	private List<AttendanceSummary> loadAttendanceSummary(LocalDate attendanceDate) {
		List<AttendanceSummary> raw = jdbcTemplate.query(
				"""
				select status, count(*) as total
				from attendance
				where date = ?
				group by status
				order by status
				""",
				(rs, rowNum) -> new AttendanceSummary(
						rs.getString("status"),
						rs.getInt("total")
				),
				Date.valueOf(attendanceDate)
		);
		int present = raw.stream()
				.filter(item -> "Present".equalsIgnoreCase(item.status()))
				.mapToInt(AttendanceSummary::total)
				.findFirst()
				.orElse(0);
		int absent = raw.stream()
				.filter(item -> "Absent".equalsIgnoreCase(item.status()))
				.mapToInt(AttendanceSummary::total)
				.findFirst()
				.orElse(0);
		return List.of(
				new AttendanceSummary("Present", present),
				new AttendanceSummary("Absent", absent)
		);
	}

	private PayrollBasics loadPayrollBasics() {
		return jdbcTemplate.query(
				"""
				select coalesce(sum(salary),0) as total_payroll,
				       coalesce(avg(salary),0) as avg_salary,
				       coalesce(max(salary),0) as max_salary,
				       coalesce(min(salary),0) as min_salary
				from employees
				""",
				(rs, rowNum) -> new PayrollBasics(
						rs.getBigDecimal("total_payroll"),
						rs.getBigDecimal("avg_salary"),
						rs.getBigDecimal("max_salary"),
						rs.getBigDecimal("min_salary")
				)
		).stream().findFirst().orElse(PayrollBasics.empty());
	}

	private static int valueOrZero(Integer value) {
		return value == null ? 0 : value;
	}

	private String formatEmployeeName(int id, String firstName, String lastName) {
		String first = firstName == null ? "" : firstName.trim();
		String last = lastName == null ? "" : lastName.trim();
		boolean placeholder = first.matches("(?i)^first\\d+$") && last.matches("(?i)^last\\d+$");
		if (placeholder || (first + last).isBlank()) {
			return fallbackName(id);
		}
		return (first + " " + last).trim();
	}

	private String fallbackName(int id) {
		String[] firstNames = {
				"Alex", "Bianca", "Carlo", "Dani", "Erika", "Francis", "Gia", "Hannah",
				"Ian", "Jessa", "Kevin", "Lara", "Marco", "Nina", "Owen", "Paula",
				"Rafael", "Sophie", "Tristan", "Yasmin"
		};
		String[] lastNames = {
				"Santos", "Reyes", "Garcia", "Cruz", "Torres", "Flores", "Navarro", "Rivera",
				"Diaz", "Lopez", "Villanueva", "Mendoza", "Castillo", "Ramos", "Salazar",
				"Velasco", "Delos Reyes", "Aguilar", "Pascual", "Serrano"
		};
		int index = Math.max(id, 1) - 1;
		String first = firstNames[index % firstNames.length];
		String last = lastNames[index % lastNames.length];
		return first + " " + last;
	}

	public record HrmSnapshot(
			int totalEmployees,
			int departmentCount,
			int newHires,
			int presentToday,
			int absentToday,
			int attendanceRate,
			String periodLabel
	) {
		public static HrmSnapshot empty() {
			return new HrmSnapshot(0, 0, 0, 0, 0, 0, "As of today");
		}
	}

	public record EmployeeRow(
			int id,
			String name,
			String position,
			String department,
			LocalDate hireDate,
			BigDecimal salary
	) { }

	public record AttendanceRow(
			int id,
			LocalDate date,
			String name,
			String department,
			String status
	) { }

	public record AttendanceSummary(String status, int total) {
		public static List<AttendanceSummary> empty() {
			return List.of(
					new AttendanceSummary("Present", 0),
					new AttendanceSummary("Absent", 0)
			);
		}
	}

	public record PayrollBasics(
			BigDecimal totalPayroll,
			BigDecimal avgSalary,
			BigDecimal maxSalary,
			BigDecimal minSalary
	) {
		public static PayrollBasics empty() {
			return new PayrollBasics(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
		}
	}
}
