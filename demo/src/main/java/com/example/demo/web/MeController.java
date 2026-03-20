package com.example.demo.web;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MeController {

	@GetMapping("/api/me")
	public ResponseEntity<UserProfile> me(HttpSession session) {
		UserProfile userProfile = SessionUtil.getUser(session);
		if (userProfile == null) {
			return ResponseEntity.status(401).build();
		}
		return ResponseEntity.ok(userProfile);
	}
}
