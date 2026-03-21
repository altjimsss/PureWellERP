package com.example.demo.web;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AppShellController {

	@GetMapping("/app")
	public String appShell(Model model, HttpSession session) {
		return buildShell(model, session, "/");
	}

	@GetMapping("/app/finance")
	public String appFinance(Model model, HttpSession session) {
		return buildShell(model, session, "/modules/finance");
	}

	@GetMapping("/app/hrm")
	public String appHrm(Model model, HttpSession session) {
		return buildShell(model, session, "/modules/hrm");
	}

	@GetMapping("/app/procurement")
	public String appProcurement(Model model, HttpSession session) {
		return buildShell(model, session, "/modules/procurement");
	}

	@GetMapping("/app/sales")
	public String appSales(Model model, HttpSession session) {
		return buildShell(model, session, "/modules/sales");
	}

	private String buildShell(Model model, HttpSession session, String initialUrl) {
		UserProfile userProfile = SessionUtil.getUser(session);
		if (userProfile == null) {
			return "redirect:/login";
		}
		model.addAttribute("siteTitle", "PureWell Refilling Station ERP");
		model.addAttribute("userProfile", userProfile);
		model.addAttribute("initialUrl", initialUrl);
		return "app-shell";
	}
}
