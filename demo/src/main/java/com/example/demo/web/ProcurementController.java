package com.example.demo.web;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ProcurementController {

	@GetMapping("/modules/procurement")
	public String procurementModule(Model model, HttpSession session) {
		UserProfile userProfile = SessionUtil.getUser(session);
		if (userProfile == null) {
			return "redirect:/login";
		}
		model.addAttribute("siteTitle", "Procurement and Inventory Module");
		model.addAttribute("moduleName", "Procurement and Inventory");
		model.addAttribute("userProfile", userProfile);
		return "procurement-module";
	}
}
