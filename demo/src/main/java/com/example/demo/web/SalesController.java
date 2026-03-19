package com.example.demo.web;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SalesController {

	@GetMapping("/modules/sales")
	public String salesModule(Model model, HttpSession session) {
		UserProfile userProfile = SessionUtil.getUser(session);
		if (userProfile == null) {
			return "redirect:/login";
		}
		model.addAttribute("siteTitle", "Sales and Delivery Module");
		model.addAttribute("moduleName", "Sales and Delivery");
		model.addAttribute("userProfile", userProfile);
		return "sales-module";
	}
}
