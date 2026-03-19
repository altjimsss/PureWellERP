package com.example.demo.web;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class FinanceController {

	@GetMapping("/modules/finance")
	public String financeModule(Model model, HttpSession session) {
		UserProfile userProfile = SessionUtil.getUser(session);
		if (userProfile == null) {
			return "redirect:/login";
		}
		model.addAttribute("siteTitle", "Finance and Accounting Module");
		model.addAttribute("moduleName", "Finance and Accounting");
		model.addAttribute("userProfile", userProfile);
		return "finance-module";
	}
}
