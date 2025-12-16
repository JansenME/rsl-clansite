package com.rsl.clansite.controller;

import com.rsl.clansite.service.CommonsService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ProfileController {
    private final CommonsService commonsService;

    public ProfileController(CommonsService commonsService) {
        this.commonsService = commonsService;
    }

    @GetMapping("/profile")
    public String viewProfile(Model model, Authentication authentication) {
        commonsService.fillModel(model, authentication);

        return "profile";
    }
}
