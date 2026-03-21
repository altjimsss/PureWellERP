package com.example.demo.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class SpaController {

	@RequestMapping({"/spa", "/spa/"})
	public String forwardSpa() {
		return "forward:/spa/index.html";
	}
}
