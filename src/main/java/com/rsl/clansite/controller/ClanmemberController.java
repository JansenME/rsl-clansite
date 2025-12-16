package com.rsl.clansite.controller;

import com.rsl.clansite.model.dto.NewClanmemberDTO;
import com.rsl.clansite.model.entity.ClanmemberEntity;
import com.rsl.clansite.model.enums.ClanRank;
import com.rsl.clansite.service.ClanmemberService;
import com.rsl.clansite.service.CommonsService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

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
    public String viewClanmembers(Model model, Authentication authentication, HttpSession session) {
        commonsService.fillModel(model);
        model.addAttribute("clanmemberViewData", clanmemberService.getUserViewData(authentication));

        String currentDiscordId = authentication.getName();

        List<ClanmemberEntity> linkedMembers = clanmemberService.getLinkedClanmembers(currentDiscordId);

        String activeMemberId = (String) session.getAttribute("ACTIVE_MEMBER_ID");

        if (activeMemberId == null && !linkedMembers.isEmpty()) {
            activeMemberId = linkedMembers.get(0).getId().toHexString();
            session.setAttribute("ACTIVE_MEMBER_ID", activeMemberId);
        }

        model.addAttribute("linkedMembers", linkedMembers);
        model.addAttribute("activeMemberId", activeMemberId);

        model.addAttribute("clanmembers", clanmemberService.findAllClanmemberEntities());
        return "clanmembers";
    }

    @PostMapping("/switch")
    public String switchAccount(
            @RequestParam("memberId") String newActiveMemberId,
            Authentication authentication,
            HttpSession session) {

        String currentDiscordId = authentication.getName();

        List<ClanmemberEntity> ownedAccounts = clanmemberService.getLinkedClanmembers(currentDiscordId);

        boolean isOwned = ownedAccounts.stream()
                .anyMatch(member -> member.getId().toHexString().equals(newActiveMemberId));

        if (isOwned) {
            session.setAttribute("ACTIVE_MEMBER_ID", newActiveMemberId);
        }

        return "redirect:/clanmembers";
    }

    @GetMapping("/add")
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINATOR')")
    public String addClanmemberForm(
            @RequestParam(value = "discordId", required = false) String discordId,
            @RequestParam(value = "skipLookup", required = false) boolean skipLookup,
            Model model) {

        model.addAttribute("clanRanks", ClanRank.values());
        NewClanmemberDTO dto = new NewClanmemberDTO();

        model.addAttribute("lookupSuccess", false);
        model.addAttribute("lookupError", "");
        model.addAttribute("altAccountWarning", false);

        if (skipLookup) {
            model.addAttribute("lookupSuccess", true);
        } else if (discordId != null && !discordId.isBlank()) {
            try {
                dto = clanmemberService.lookupDiscordUser(discordId);
                model.addAttribute("lookupSuccess", true);

                if (clanmemberService.isDiscordIdInRoster(discordId)) {
                    model.addAttribute("altAccountWarning", true);
                }
            } catch (Exception e) {
                model.addAttribute("lookupError", "Error looking up user: " + e.getMessage());
            }
        }

        model.addAttribute("clanmemberRosterDto", dto);
        return "clanmember-add";
    }

    @PostMapping("/save")
    public String saveClanmember(
            @Valid NewClanmemberDTO dto,
            BindingResult bindingResult,
            Model model) {

        if (bindingResult.hasErrors()) {
            model.addAttribute("clanRanks", ClanRank.values());
            model.addAttribute("clanmemberRosterDto", dto);
            model.addAttribute("lookupSuccess", true);
            model.addAttribute("lookupError", "");
            return "clanmember-add";
        }

        if (clanmemberService.isPlayerIngameNameInUse(dto.getIngameName())) {
            model.addAttribute("clanRanks", ClanRank.values());
            model.addAttribute("clanmemberRosterDto", dto);

            model.addAttribute("lookupError", "Failed to save member: The In-Game Name '" + dto.getIngameName() + "' already exists in the roster.");
            model.addAttribute("lookupSuccess", true);

            model.addAttribute("altAccountWarning", false);

            return "clanmember-add";
        }

        try {
            clanmemberService.saveNewClanmember(dto);
        } catch (Exception e) {
            model.addAttribute("clanRanks", ClanRank.values());
            model.addAttribute("clanmemberRosterDto", dto);
            model.addAttribute("lookupError", "Failed to save member: " + e.getMessage());
            model.addAttribute("lookupSuccess", true);
            model.addAttribute("altAccountWarning", false);
            return "clanmember-add";
        }

        return "redirect:/clanmembers";
    }
}
