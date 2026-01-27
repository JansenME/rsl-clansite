package com.rsl.clansite.controller;

import com.rsl.clansite.service.ClanmemberService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private final ClanmemberService clanmemberService;

    public AdminController(ClanmemberService clanmemberService) {
        this.clanmemberService = clanmemberService;
    }

    @PostMapping("/masquerade")
    @ResponseBody
    public void setMasquerade(@RequestParam("role") String role, Authentication authentication) {
        String discordId = authentication.getName();

        // "NONE" means clear the impersonation
        String roleToSet = "NONE".equals(role) ? null : role;

        clanmemberService.updateImpersonation(discordId, roleToSet);
    }
}