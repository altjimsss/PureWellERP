package com.example.demo.web;

import jakarta.servlet.http.HttpSession;

public final class SessionUtil {

	public static final String USER_KEY = "userProfile";

	private SessionUtil() {
	}

	public static UserProfile getUser(HttpSession session) {
		if (session == null) {
			return null;
		}
		Object value = session.getAttribute(USER_KEY);
		if (value instanceof UserProfile profile) {
			return profile;
		}
		return null;
	}

	public static void setUser(HttpSession session, UserProfile profile) {
		if (session == null) {
			return;
		}
		session.setAttribute(USER_KEY, profile);
	}

	public static void clearUser(HttpSession session) {
		if (session == null) {
			return;
		}
		session.removeAttribute(USER_KEY);
	}
}
