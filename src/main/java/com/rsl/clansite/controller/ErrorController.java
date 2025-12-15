package com.rsl.clansite.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/login-error")
public class ErrorController {
    @GetMapping("/unlinked")
    public String handleUnlinkedAccountError(@RequestParam("error") String errorMessage, Model model) {
        model.addAttribute("errorMessage", errorMessage);
        model.addAttribute("contact", "Please contact the administrator on Discord to link your account to the clan roster.");

        return "unlinked";
    }

    @GetMapping
    public String handleGenericError(Model model) {
        model.addAttribute("errorMessage", "An unknown authentication error occurred. Please try again or contact support.");

        return "error";
    }
}
