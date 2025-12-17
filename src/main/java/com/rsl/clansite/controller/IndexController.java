package com.rsl.clansite.controller;

import com.rsl.clansite.service.CommonsService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

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

    @GetMapping("/woopsie")
    public String triggerError() {
        throw new RuntimeException("Test Exception for 500 page");
    }
}
