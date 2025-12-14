package com.rsl.clansite.controller;

import com.rsl.clansite.service.ClanmemberService;
import com.rsl.clansite.service.CommonsService;
import com.rsl.clansite.service.DiscordRoleService;
import org.springframework.security.core.Authentication;
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

        if(authentication != null && authentication.getPrincipal() instanceof OAuth2User oauth2User) {
            String globalName = oauth2User.getAttribute("global_name");

            if (globalName != null) {
                model.addAttribute("discordUserName", globalName);
            }

            Object rawRolesObject = oauth2User.getAttributes().get("rawDiscordRoleIds");

            if(rawRolesObject instanceof Set) {
                @SuppressWarnings("unchecked")
                Set<String> serverRoleIds = (Set<String>) rawRolesObject;
                final List<String> masterOrder = discordRoleService.getOrderedRoleIds();
                List<String> userRoleIds = new ArrayList<>(serverRoleIds);

                userRoleIds.sort((id1, id2) -> {
                    int index1 = masterOrder.indexOf(id1);
                    int index2 = masterOrder.indexOf(id2);

                    if (index1 == -1 && index2 == -1) return 0;
                    if (index1 == -1) return 1;
                    if (index2 == -1) return -1;

                    return Integer.compare(index1, index2);
                });

                List<String> roleNames = userRoleIds.stream()
                        .map(discordRoleService::getRoleName)
                        .toList();

                model.addAttribute("discordUserRoles", roleNames);
            } else {
                model.addAttribute("discordUserRoles", List.of("No Discord Roles Found"));
            }
        }

        return "clanmembers";
    }
}
