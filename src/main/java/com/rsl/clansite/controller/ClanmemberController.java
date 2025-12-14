package com.rsl.clansite.controller;

import com.rsl.clansite.model.ClanmemberViewData;
import com.rsl.clansite.service.ClanmemberService;
import com.rsl.clansite.service.CommonsService;
import com.rsl.clansite.service.DiscordRoleService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/clanmembers")
public class ClanmemberController {
    private final CommonsService commonsService;
    private final ClanmemberService clanmemberService;
    private final DiscordRoleService discordRoleService;

    public ClanmemberController(final CommonsService commonsService, final ClanmemberService clanmemberService, final DiscordRoleService discordRoleService) {
        this.commonsService = commonsService;
        this.clanmemberService = clanmemberService;
        this.discordRoleService = discordRoleService;
    }

    @GetMapping(value={"", "/"})
    public String viewClanmembers(Model model, Authentication authentication) {
        commonsService.fillModel(model);
        model.addAttribute("clanmembers", clanmemberService.findAllMembers());

        ClanmemberViewData userViewData = clanmemberService.getUserViewData(authentication);

        if (userViewData.getDiscordUserName() != null) {
            model.addAttribute("discordUserName", userViewData.getDiscordUserName());
            model.addAttribute("discordUserRoles", userViewData.getDiscordUserRoles());
        }

        return "clanmembers";
    }
}
