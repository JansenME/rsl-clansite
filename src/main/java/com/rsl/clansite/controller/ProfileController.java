package com.rsl.clansite.controller;

import com.rsl.clansite.model.ClanmemberViewData;
import com.rsl.clansite.model.entity.ClanmemberEntity;
import com.rsl.clansite.service.ClanmemberService;
import com.rsl.clansite.service.CommonsService;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@Controller
public class ProfileController {
    private final CommonsService commonsService;
    private final ClanmemberService clanmemberService;

    public ProfileController(CommonsService commonsService, ClanmemberService clanmemberService) {
        this.commonsService = commonsService;
        this.clanmemberService = clanmemberService;
    }

    @GetMapping("/profile")
    @PreAuthorize("isAuthenticated()")
    public String profileRedirect(Authentication authentication, HttpSession session, Model model) {
        String activeMemberId = clanmemberService.manageActiveMemberSession(session, authentication);

        if (activeMemberId != null) {
            return "redirect:/profile/" + activeMemberId;
        }

        commonsService.fillModel(model, authentication);
        model.addAttribute("isOwnProfile", true);
        return "profile";
    }

    @GetMapping("/profile/{id}")
    @PreAuthorize("isAuthenticated()")
    public String viewMemberProfile(@PathVariable String id, Model model, Authentication authentication) {
        commonsService.fillModel(model, authentication);

        ClanmemberEntity targetMember = clanmemberService.getMemberById(id);

        String currentDiscordId = authentication.getName();
        List<ClanmemberEntity> myAccounts = clanmemberService.getLinkedClanmembers(currentDiscordId);

        boolean isOwnProfile = myAccounts.stream()
                .anyMatch(account -> account.getId().toHexString().equals(id));

        boolean isMember = authentication.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_MEMBER"));

        if (!isOwnProfile && !isMember) {
            throw new AccessDeniedException("You do not have permission to view other member profiles.");
        }

        ClanmemberViewData targetViewData = clanmemberService.getViewDataForMember(targetMember);
        model.addAttribute("clanmemberViewData", targetViewData);
        model.addAttribute("isOwnProfile", isOwnProfile);

        return "profile";
    }
}
