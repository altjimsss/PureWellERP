package com.example.demo.web;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HrmController {

	@GetMapping("/modules/hrm")
	public String hrmModule(Model model, HttpSession session) {
		UserProfile userProfile = SessionUtil.getUser(session);
		if (userProfile == null) {
			return "redirect:/login";
		}
		model.addAttribute("siteTitle", "Human Resource Management Module");
		model.addAttribute("moduleName", "Human Resource Management");
		model.addAttribute("userProfile", userProfile);
		return "hrm-module";
	}
}
