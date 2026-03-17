package com.example.demo.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class FinanceController {

	@GetMapping("/modules/finance")
	public String financeModule(Model model) {
		model.addAttribute("siteTitle", "Finance and Accounting Module");
		model.addAttribute("moduleName", "Finance and Accounting");
		return "finance-module";
	}
}
