package com.rsl.clansite.controller;

import com.rsl.clansite.service.CommonsService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class IndexController {
    private final CommonsService commonsService;

    public IndexController(final CommonsService commonsService) {
        this.commonsService = commonsService;
    }

    @GetMapping(value={"", "/", "/index"})
    public String index(Model model, Authentication authentication) {
        commonsService.fillModel(model, authentication);

        return "index";
    }

    @GetMapping("/login")
    public String loginPage(@RequestParam(value = "error", required = false) String error,
                            Model model,
                            Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()) {
            return "redirect:/profile";
        }
        commonsService.fillModel(model, authentication);

        if (error != null) {
            model.addAttribute("loginError", error);
        }
        return "login";
    }
}
