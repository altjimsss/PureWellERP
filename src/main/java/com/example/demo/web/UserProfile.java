package com.example.demo.web;

public record UserProfile(
		int employeeId,
		String fullName,
		String role,
		String position,
		String department,
		String initials,
		String profilePicUrl
) { }
