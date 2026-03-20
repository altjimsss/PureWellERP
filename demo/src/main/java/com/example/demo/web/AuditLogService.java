package com.example.demo.web;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AuditLogService {
	private final JdbcTemplate jdbcTemplate;

	public AuditLogService(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public void log(String action, String entity, String entityId, String details, UserProfile userProfile) {
		if (userProfile == null) {
			return;
		}
		jdbcTemplate.update(
				"""
				insert into audit_logs(action, entity, entity_id, details, actor_id, actor_name, actor_role)
				values(?, ?, ?, ?, ?, ?, ?)
				""",
				action,
				entity,
				entityId,
				details,
				userProfile.employeeId(),
				userProfile.fullName(),
				userProfile.role()
		);
	}
}
