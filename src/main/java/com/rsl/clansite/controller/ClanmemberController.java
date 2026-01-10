package com.rsl.clansite.controller;

import com.rsl.clansite.model.dto.MemberLookupResult;
import com.rsl.clansite.model.dto.NewClanmemberDTO;
import com.rsl.clansite.model.entity.ClanmemberEntity;
import com.rsl.clansite.model.entity.VisitorLogEntity;
import com.rsl.clansite.model.enums.ClanRank;
import com.rsl.clansite.repository.VisitorLogRepository;
import com.rsl.clansite.service.ClanmemberService;
import com.rsl.clansite.service.CommonsService;
import com.rsl.clansite.validation.OnCreate;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.groups.Default;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
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
    private final VisitorLogRepository visitorLogRepository;

    public ClanmemberController(final CommonsService commonsService,
                                final ClanmemberService clanmemberService,
                                final VisitorLogRepository visitorLogRepository) {
        this.commonsService = commonsService;
        this.clanmemberService = clanmemberService;
        this.visitorLogRepository = visitorLogRepository;
    }

    @GetMapping(value={"", "/"})
    public String viewClanmembers(Model model, Authentication authentication, HttpSession session) {
        commonsService.fillModel(model, authentication);

        String activeMemberId = clanmemberService.manageActiveMemberSession(session, authentication);

        model.addAttribute("linkedMembers", clanmemberService.getLinkedClanmembers(authentication != null ? authentication.getName() : null));
        model.addAttribute("activeMemberId", activeMemberId);
        model.addAttribute("clanmembers", clanmemberService.findAllClanmemberEntities());

        return "clanmembers";
    }

    @GetMapping("/admin/login-history")
    @PreAuthorize("hasRole('ADMIN')")
    public String viewLoginHistory(Model model, Authentication authentication) {
        commonsService.fillModel(model, authentication);

        List<ClanmemberEntity> members = clanmemberService.findAllClanmemberEntities().stream()
                .filter(m -> m.getLastLogin() != null)
                .sorted((m1, m2) -> m2.getLastLogin().compareTo(m1.getLastLogin()))
                .toList();

        List<VisitorLogEntity> visitors = visitorLogRepository.findAll().stream()
                .sorted((v1, v2) -> v2.getLastLogin().compareTo(v1.getLastLogin()))
                .toList();

        model.addAttribute("memberHistory", members);
        model.addAttribute("visitorHistory", visitors);

        return "login-history";
    }

    @PostMapping("/switch")
    @PreAuthorize("isAuthenticated()")
    public String switchAccount(
            @RequestParam("memberId") String newActiveMemberId,
            Authentication authentication,
            HttpSession session) {

        clanmemberService.switchActiveMember(session, authentication, newActiveMemberId);

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

        if (skipLookup) {
            model.addAttribute("lookupSuccess", true);
        } else if (discordId != null && !discordId.isBlank()) {
            MemberLookupResult result = clanmemberService.performMemberLookup(discordId);

            if (result.isSuccess()) {
                model.addAttribute("clanmemberRosterDto", result.getDto());
                model.addAttribute("lookupSuccess", true);
                if (result.getWarningMessage() != null) {
                    model.addAttribute("lookupWarning", result.getWarningMessage());
                }
            } else {
                model.addAttribute("lookupError", result.getErrorMessage());
            }
        }

        return "clanmember-add";
    }

    @PostMapping("/save")
    @PreAuthorize("hasRole('ADMIN')")
    public String saveClanmember(
            @ModelAttribute("clanmemberRosterDto") @Validated({Default.class, OnCreate.class}) NewClanmemberDTO dto,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes,
            Authentication authentication) {
        boolean manualEntryError = !StringUtils.hasText(dto.getDiscordId()) && !StringUtils.hasText(dto.getIngameName());

        if (bindingResult.hasErrors() || manualEntryError) {
            StringBuilder errorMsg = new StringBuilder();

            bindingResult.getAllErrors().forEach(error ->
                    errorMsg.append(error.getDefaultMessage()).append(" ")
            );

            if (manualEntryError) {
                errorMsg.append("For manual entries, the In-Game Name is required so we know who this is! ");
            }

            return reloadFormWithError(model, dto, errorMsg.toString().trim());
        }

        try {
            clanmemberService.saveNewClanmember(dto, authentication);
        } catch (Exception e) {
            return reloadFormWithError(model, dto, "Failed to save member: " + e.getMessage());
        }

        return "redirect:/clanmembers";
    }

    @GetMapping("/edit/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public String editClanmemberForm(@PathVariable String id, Model model, Authentication authentication) {
        commonsService.fillModel(model, authentication);

        ClanmemberEntity member = clanmemberService.getMemberById(id);
        NewClanmemberDTO dto = clanmemberService.mapEntityToDto(member);

        model.addAttribute("clanmemberRosterDto", dto);
        model.addAttribute("clanRanks", ClanRank.values());
        model.addAttribute("editingMemberId", id);

        return "clanmember-edit";
    }

    @PostMapping("/edit/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public String updateClanmember(
            @PathVariable String id,
            @ModelAttribute("clanmemberRosterDto") @Validated(Default.class) NewClanmemberDTO dto,
            BindingResult bindingResult,
            Model model,
            Authentication authentication) {

        if (bindingResult.hasErrors()) {
            StringBuilder errorMsg = new StringBuilder();
            bindingResult.getAllErrors().forEach(error ->
                    errorMsg.append(error.getDefaultMessage()).append(" ")
            );

            commonsService.fillModel(model, authentication);
            model.addAttribute("clanRanks", ClanRank.values());
            model.addAttribute("editingMemberId", id);
            model.addAttribute("errorMessage", errorMsg.toString());
            return "clanmember-edit";
        }

        try {
            clanmemberService.updateClanmember(id, dto, authentication);
        } catch (Exception e) {
            commonsService.fillModel(model, authentication);
            model.addAttribute("clanRanks", ClanRank.values());
            model.addAttribute("editingMemberId", id);
            model.addAttribute("errorMessage", e.getMessage());
            return "clanmember-edit";
        }

        return "redirect:/clanmembers";
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasRole('ADMIN')")
    public String deleteClanmember(@PathVariable String id, HttpSession session, Authentication authentication) {
        clanmemberService.deleteById(id, session, authentication);
        return "redirect:/clanmembers";
    }

    private String reloadFormWithError(Model model, NewClanmemberDTO dto, String errorMessage) {
        model.addAttribute("clanRanks", ClanRank.values());
        model.addAttribute("clanmemberRosterDto", dto);
        model.addAttribute("lookupSuccess", true);
        if (errorMessage != null) {
            model.addAttribute("lookupError", errorMessage);
        }
        return "clanmember-add";
    }
}
