package com.example.demo.web;

import jakarta.servlet.http.HttpSession;
import java.util.List;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class LoginController {

	private final JdbcTemplate jdbcTemplate;
	private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

	public LoginController(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@GetMapping("/login")
	public String login(Model model) {
		model.addAttribute("siteTitle", "PureWell ERP Login");
		model.addAttribute("error", null);
		return "login";
	}

	@PostMapping("/login")
	public String handleLogin(
			@RequestParam("username") String username,
			@RequestParam("password") String password,
			HttpSession session,
			Model model
	) {
		String trimmedUser = username == null ? "" : username.trim();
		String trimmedPass = password == null ? "" : password.trim();
		if (trimmedUser.isBlank()) {
			return renderError(model, "Please enter your username.");
		}
		try {
			List<AuthAccount> matches = jdbcTemplate.query(
					"""
					select ua.employee_id,
					       ua.username,
					       ua.password_hash,
					       ua.role,
					       ua.profile_pic_url,
					       e.first_name,
					       e.last_name,
					       e.position,
					       e.department
					from user_accounts ua
					join employees e on ua.employee_id = e.id
					where lower(ua.username) = lower(?)
					""",
					(rs, rowNum) -> new AuthAccount(
							rs.getInt("employee_id"),
							rs.getString("username"),
							rs.getString("password_hash"),
							rs.getString("role"),
							rs.getString("profile_pic_url"),
							rs.getString("first_name"),
							rs.getString("last_name"),
							rs.getString("position"),
							rs.getString("department")
					),
					trimmedUser
			);
			if (matches.isEmpty()) {
				return renderError(model, "Username not found.");
			}
			AuthAccount account = matches.get(0);
			if (!passwordMatches(trimmedPass, account.passwordHash())) {
				return renderError(model, "Invalid password.");
			}
			String fullName = (safe(account.firstName()) + " " + safe(account.lastName())).trim();
			String initials = initialsFor(account.firstName(), account.lastName());
			UserProfile profile = new UserProfile(
					account.employeeId(),
					fullName.isBlank() ? "Employee " + account.employeeId() : fullName,
					normalizeRole(account.role()),
					account.position(),
					account.department(),
					initials,
					account.profilePicUrl()
			);
			SessionUtil.setUser(session, profile);
			return "redirect:/spa/";
		} catch (DataAccessException ex) {
			return renderError(model, "Unable to reach the database. Please try again.");
		}
	}

	@GetMapping("/logout")
	public String logout(HttpSession session) {
		SessionUtil.clearUser(session);
		return "redirect:/login";
	}

	private String renderError(Model model, String message) {
		model.addAttribute("siteTitle", "PureWell ERP Login");
		model.addAttribute("error", message);
		return "login";
	}

	private static String normalizeRole(String role) {
		if (role == null) {
			return "Staff";
		}
		String normalized = role.trim().toLowerCase();
		if ("admin".equals(normalized)) {
			return "Admin";
		}
		return "Staff";
	}

	private boolean passwordMatches(String raw, String storedHash) {
		if (storedHash == null || storedHash.isBlank()) {
			return raw == null || raw.isBlank();
		}
		String trimmedHash = storedHash.trim();
		if (trimmedHash.startsWith("$2a$") || trimmedHash.startsWith("$2b$") || trimmedHash.startsWith("$2y$")) {
			return passwordEncoder.matches(raw, trimmedHash);
		}
		return raw.equals(trimmedHash);
	}

	private static String safe(String value) {
		return value == null ? "" : value.trim();
	}

	private static String initialsFor(String firstName, String lastName) {
		String first = safe(firstName);
		String last = safe(lastName);
		StringBuilder initials = new StringBuilder();
		if (!first.isBlank()) {
			initials.append(first.substring(0, 1));
		}
		if (!last.isBlank()) {
			initials.append(last.substring(0, 1));
		}
		if (initials.length() == 0) {
			return "PW";
		}
		return initials.toString().toUpperCase();
	}

	private record AuthAccount(
			int employeeId,
			String username,
			String passwordHash,
			String role,
			String profilePicUrl,
			String firstName,
			String lastName,
			String position,
			String department
	) { }
}
