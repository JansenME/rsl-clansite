package com.rsl.clansite.controller;

import com.rsl.clansite.model.dto.NewClanmemberDTO;
import com.rsl.clansite.model.entity.AuditLogEntity;
import com.rsl.clansite.model.entity.ClanmemberEntity;
import com.rsl.clansite.model.enums.ClanRank;
import com.rsl.clansite.service.ClanmemberService;
import com.rsl.clansite.service.CommonsService;
import com.rsl.clansite.service.DiscordRoleService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

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
        commonsService.fillModel(model, authentication);

        List<ClanmemberEntity> linkedMembers = List.of();
        String activeMemberId = null;

        if (authentication != null && authentication.isAuthenticated()) {
            String currentDiscordId = authentication.getName();
            linkedMembers = clanmemberService.getLinkedClanmembers(currentDiscordId);

            activeMemberId = (String) session.getAttribute("ACTIVE_MEMBER_ID");

            if (activeMemberId == null && !linkedMembers.isEmpty()) {
                activeMemberId = linkedMembers.get(0).getId().toHexString();
                session.setAttribute("ACTIVE_MEMBER_ID", activeMemberId);
            }
        }

        model.addAttribute("linkedMembers", linkedMembers);
        model.addAttribute("activeMemberId", activeMemberId);

        model.addAttribute("clanmembers", clanmemberService.findAllClanmemberEntities());
        return "clanmembers";
    }

    @PostMapping("/switch")
    @PreAuthorize("isAuthenticated()")
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
    @PreAuthorize("hasRole('ADMIN')")
    public String addClanmemberForm(
            @RequestParam(value = "discordId", required = false) String discordId,
            @RequestParam(value = "skipLookup", required = false) boolean skipLookup,
            Model model,
            Authentication authentication) {
        commonsService.fillModel(model, authentication);
        model.addAttribute("clanRanks", ClanRank.values());

        if (!model.containsAttribute("clanmemberRosterDto")) {
            model.addAttribute("clanmemberRosterDto", new NewClanmemberDTO());
        }

        NewClanmemberDTO dto = (NewClanmemberDTO) model.getAttribute("clanmemberRosterDto");

        if (skipLookup) {
            model.addAttribute("lookupSuccess", true);
        } else if (discordId != null && !discordId.isBlank()) {
            try {
                NewClanmemberDTO lookedUpDto = clanmemberService.lookupDiscordUser(discordId);

                dto.setDiscordId(lookedUpDto.getDiscordId());
                dto.setDiscordName(lookedUpDto.getDiscordName());
                dto.setPlayerNickname(lookedUpDto.getPlayerNickname());
                dto.setAvatarHash(lookedUpDto.getAvatarHash());
                dto.setDiscordRoles(lookedUpDto.getDiscordRoles());
                dto.setClanGroup(lookedUpDto.getClanGroup());

                model.addAttribute("lookupSuccess", true);

                StringBuilder warningMsg = new StringBuilder();

                List<String> roles = dto.getDiscordRoles();
                if (roles != null &&
                        roles.contains(DiscordRoleService.T1_ROLE_ID) &&
                        roles.contains(DiscordRoleService.T2_ROLE_ID)) {
                    warningMsg.append("Notice: This user has both T1 and T2 roles in Discord. Please manually select the correct Clan Group below. ");
                }

                if (clanmemberService.isDiscordIdInRoster(discordId)) {
                    warningMsg.append("Notice: This Discord ID is already in the roster. You can still add this as an alt account.");
                }

                if (warningMsg.length() > 0) {
                    model.addAttribute("lookupWarning", warningMsg.toString().trim());
                }
            } catch (Exception e) {
                model.addAttribute("lookupError", "Error looking up user: " + e.getMessage());
            }
        }

        model.addAttribute("clanmemberRosterDto", dto);
        return "clanmember-add";
    }

    @PostMapping("/save")
    @PreAuthorize("hasRole('ADMIN')")
    public String saveClanmember(
            @Valid NewClanmemberDTO dto,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes,
            Authentication authentication) {

        boolean isManualEntry = (dto.getDiscordId() == null || dto.getDiscordId().isBlank());

        if (bindingResult.hasErrors()) {
            model.addAttribute("clanRanks", ClanRank.values());
            model.addAttribute("clanmemberRosterDto", dto);
            model.addAttribute("lookupSuccess", true);
            return "clanmember-add";
        }

        if (clanmemberService.isPlayerIngameNameInUse(dto.getIngameName())) {
            model.addAttribute("clanRanks", ClanRank.values());
            model.addAttribute("clanmemberRosterDto", dto);
            model.addAttribute("lookupError", "Failed to save member: The In-Game Name '" + dto.getIngameName() + "' already exists in the roster.");
            model.addAttribute("lookupSuccess", true);

            return "clanmember-add";
        }

        if (isManualEntry && (dto.getIngameName() == null || dto.getIngameName().isBlank())) {
            redirectAttributes.addFlashAttribute("lookupError", "For manual entries, the In-Game Name is required so we know who this is!");
            redirectAttributes.addFlashAttribute("clanmemberRosterDto", dto);
            return "redirect:/clanmembers/add?skipLookup=true";
        }

        try {
            clanmemberService.saveNewClanmember(dto, authentication);
        } catch (Exception e) {
            model.addAttribute("clanRanks", ClanRank.values());
            model.addAttribute("clanmemberRosterDto", dto);
            model.addAttribute("lookupError", "Failed to save member: " + e.getMessage());
            model.addAttribute("lookupSuccess", true);
            return "clanmember-add";
        }

        return "redirect:/clanmembers";
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasRole('ADMIN')")
    public String deleteClanmember(@PathVariable String id, HttpSession session, Authentication authentication) {
        clanmemberService.deleteById(id, session, authentication);
        return "redirect:/clanmembers";
    }
}
