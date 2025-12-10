package com.rsl.clansite.controller;

import com.rsl.clansite.service.CommonsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class IndexController {
    private final CommonsService commonsService;

    @Autowired
    public IndexController(final CommonsService commonsService) {
        this.commonsService = commonsService;
    }

    @GetMapping(value={"", "/", "/index"})
    public String index(Model model) {
        commonsService.fillModel(model);

        return "index";
    }
}
