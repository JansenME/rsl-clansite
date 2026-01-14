package com.rsl.clansite.controller;

import com.rsl.clansite.model.Champion;
import com.rsl.clansite.model.ClanmemberViewData;
import com.rsl.clansite.model.CompleteChampionsFilter;
import com.rsl.clansite.model.entity.ClanmemberEntity;
import com.rsl.clansite.model.enums.Affinity;
import com.rsl.clansite.model.enums.Alliance;
import com.rsl.clansite.model.enums.Faction;
import com.rsl.clansite.model.enums.Rarity;
import com.rsl.clansite.model.enums.Type;
import com.rsl.clansite.service.ChampionsService;
import com.rsl.clansite.service.ClanmemberService;
import com.rsl.clansite.service.CommonsService;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
@RequestMapping("/profile")
public class ProfileController {
    private final CommonsService commonsService;
    private final ClanmemberService clanmemberService;
    private final ChampionsService championsService;

    public ProfileController(CommonsService commonsService,
                             ClanmemberService clanmemberService,
                             ChampionsService championsService) {
        this.commonsService = commonsService;
        this.clanmemberService = clanmemberService;
        this.championsService = championsService;
    }

    @GetMapping(value={"", "/"})
    @PreAuthorize("isAuthenticated()")
    public String profileRedirect(Authentication authentication, HttpSession session, Model model) {
        String activeMemberId = clanmemberService.manageActiveMemberSession(session, authentication);

        if (activeMemberId != null) {
            return "redirect:/profile/" + activeMemberId;
        }

        commonsService.fillModel(model, authentication, session);
        model.addAttribute("isOwnProfile", true);

        model.addAttribute("champions", List.of());
        addFilterDataToModel(model);

        return "profile";
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public String viewMemberProfile(@PathVariable String id, Model model, Authentication authentication, HttpSession session) {
        commonsService.fillModel(model, authentication, session);

        ClanmemberEntity targetMember = clanmemberService.getMemberById(id);

        String currentDiscordId = ((OAuth2User) authentication.getPrincipal()).getAttribute("id");
        List<ClanmemberEntity> myAccounts = clanmemberService.getLinkedClanmembers(currentDiscordId);

        boolean isOwnProfile = myAccounts.stream()
                .anyMatch(account -> account.getId().toHexString().equals(id));

        boolean isMember = authentication.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_MEMBER"));

        if (!isOwnProfile && !isMember) {
            throw new AccessDeniedException("You do not have permission to view other member profiles.");
        }

        if (isOwnProfile) {
            model.addAttribute("linkedMembers", myAccounts);
        }

        List<Champion> roster = championsService.getChampionsByIds(targetMember.getRosterChampionIds());
        model.addAttribute("champions", roster);
        addFilterDataToModel(model);

        ClanmemberViewData targetViewData = clanmemberService.getViewDataForMember(targetMember);
        model.addAttribute("clanmemberViewData", targetViewData);
        model.addAttribute("member", targetMember);
        model.addAttribute("isOwnProfile", isOwnProfile);

        return "profile";
    }

    @PostMapping("/switch")
    @PreAuthorize("isAuthenticated()")
    public String switchAccount(
            @RequestParam("memberId") String newActiveMemberId,
            Authentication authentication,
            HttpSession session) {

        clanmemberService.switchActiveMember(session, authentication, newActiveMemberId);

        return "redirect:/profile";
    }

    private void addFilterDataToModel(Model model) {
        model.addAttribute("filtersWrapper", new CompleteChampionsFilter());
        model.addAttribute("rarities", Rarity.values());
        model.addAttribute("types", Type.values());
        model.addAttribute("affinities", Affinity.values());
        model.addAttribute("factions", Faction.values());
        model.addAttribute("alliances", Alliance.values());
    }
}