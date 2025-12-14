package com.rsl.clansite.controller;

import com.rsl.clansite.service.ClanmemberService;
import com.rsl.clansite.service.CommonsService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/clanmembers")
public class ClanmemberController {
    private final CommonsService commonsService;
    private final ClanmemberService clanmemberService;

    public ClanmemberController(final CommonsService commonsService, final ClanmemberService clanmemberService) {
        this.commonsService = commonsService;
        this.clanmemberService = clanmemberService;
    }

    @GetMapping(value={"", "/"})
    public String viewClanmembers(Model model) {
        commonsService.fillModel(model);

        model.addAttribute("clanmembers", clanmemberService.findAllMembers());

        return "clanmembers";
    }
}
