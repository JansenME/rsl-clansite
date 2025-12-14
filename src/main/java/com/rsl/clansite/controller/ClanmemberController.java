package com.rsl.clansite.controller;

import com.rsl.clansite.service.ClanmemberService;
import com.rsl.clansite.service.CommonsService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
    public String viewClanmembers(Model model, Authentication authentication) {
        commonsService.fillModel(model);

        model.addAttribute("clanmembers", clanmemberService.findAllMembers());

        if(authentication != null && authentication.getPrincipal() instanceof OAuth2User oauth2User) {
            String globalName = oauth2User.getAttribute("global_name");

            if (globalName != null) {
                model.addAttribute("discordUserName", globalName);
            }

            Object rawRolesObject = oauth2User.getAttributes().get("rawDiscordRoleIds");

            if(rawRolesObject instanceof Set) {
                @SuppressWarnings("unchecked")
                Set<String> serverRoleIds = (Set<String>) rawRolesObject;

                List<String> rawDiscordRoleIds = new ArrayList<>(serverRoleIds);

                model.addAttribute("discordUserRoles", rawDiscordRoleIds);
            } else {
                model.addAttribute("discordUserRoles", List.of("No Discord Roles Found"));
            }
        }

        return "clanmembers";
    }
}
