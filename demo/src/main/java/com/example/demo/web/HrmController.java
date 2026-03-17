package com.example.demo.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HrmController {

	@GetMapping("/modules/hrm")
	public String hrmModule(Model model) {
		model.addAttribute("siteTitle", "Human Resource Management Module");
		model.addAttribute("moduleName", "Human Resource Management");
		return "hrm-module";
	}
}
