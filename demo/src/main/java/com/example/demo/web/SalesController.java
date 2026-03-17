package com.example.demo.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SalesController {

	@GetMapping("/modules/sales")
	public String salesModule(Model model) {
		model.addAttribute("siteTitle", "Sales and Delivery Module");
		model.addAttribute("moduleName", "Sales and Delivery");
		return "sales-module";
	}
}
