package com.example.demo.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ProcurementController {

	@GetMapping("/modules/procurement")
	public String procurementModule(Model model) {
		model.addAttribute("siteTitle", "Procurement and Inventory Module");
		model.addAttribute("moduleName", "Procurement and Inventory");
		return "procurement-module";
	}
}
