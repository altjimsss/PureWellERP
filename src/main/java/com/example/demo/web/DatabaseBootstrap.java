package com.example.demo.web;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DatabaseBootstrap {
	private final JdbcTemplate jdbcTemplate;

	public DatabaseBootstrap(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@PostConstruct
	public void ensureTables() {
		jdbcTemplate.execute(
				"""
				create table if not exists tasks (
					id serial primary key,
					title text not null,
					status text not null default 'Open',
					due_date date,
					priority text default 'Normal',
					created_at timestamptz default now(),
					created_by integer
				)
				"""
		);
		jdbcTemplate.execute(
				"""
				create table if not exists notification_rules (
					id serial primary key,
					event_type text not null,
					channel text not null,
					target text not null,
					is_active boolean not null default true,
					created_at timestamptz default now(),
					created_by integer
				)
				"""
		);
		jdbcTemplate.execute(
				"""
				create table if not exists audit_logs (
					id serial primary key,
					action text not null,
					entity text not null,
					entity_id text,
					details text,
					created_at timestamptz default now(),
					actor_id integer,
					actor_name text,
					actor_role text
				)
				"""
		);
		jdbcTemplate.execute(
				"""
				create table if not exists approvals (
					id serial primary key,
					entity_type text not null,
					entity_id integer not null,
					status text not null default 'Pending',
					requested_by integer,
					requested_at timestamptz default now(),
					decided_by integer,
					decided_at timestamptz,
					notes text
				)
				"""
		);
	}
}
