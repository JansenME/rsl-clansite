package com.rsl.clansite.controller;

import com.rsl.clansite.model.Team;
import com.rsl.clansite.model.entity.ClanmemberEntity;
import com.rsl.clansite.model.entity.SiegeConditionEntity;
import com.rsl.clansite.repository.ChampionRepository;
import com.rsl.clansite.service.ClanmemberService;
import com.rsl.clansite.service.CommonsService;
import com.rsl.clansite.service.SiegeConditionService;
import jakarta.servlet.http.HttpSession;
import org.bson.types.ObjectId;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.beans.PropertyEditorSupport;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/teams")
public class TeamController {

    private final CommonsService commonsService;
    private final ClanmemberService clanmemberService;
    private final ChampionRepository championRepository;
    private final SiegeConditionService siegeConditionService;

    public TeamController(CommonsService commonsService,
                          ClanmemberService clanmemberService,
                          ChampionRepository championRepository,
                          SiegeConditionService siegeConditionService) {
        this.commonsService = commonsService;
        this.clanmemberService = clanmemberService;
        this.championRepository = championRepository;
        this.siegeConditionService = siegeConditionService;
    }

    public record ConditionDropdownItem(String id, String label, String category, String value) {}

    @GetMapping("/builder")
    @PreAuthorize("hasRole('MEMBER')")
    public String builder(@RequestParam(required = false) String editTeamId,
                          @RequestParam(required = false) String targetMemberId,
                          Model model, Authentication authentication, HttpSession session) {
        commonsService.fillModel(model, authentication);

        ClanmemberEntity activeMember;

        // 1. Determine Context: Are we editing for ourselves or someone else?
        // Hierarchy Check: ROLE_COORDINATOR is sufficient as it includes ADMIN/OWNER
        if (StringUtils.hasText(targetMemberId) && canManageOthers(authentication)) {
            activeMember = clanmemberService.getMemberById(targetMemberId);
        } else {
            activeMember = clanmemberService.getActiveClanmember(session, authentication);
        }

        if (activeMember == null) {
            return "redirect:/";
        }

        model.addAttribute("targetMemberId", activeMember.getId().toHexString());

        List<String> rosterIds = activeMember.getRosterChampionIds();
        if (rosterIds != null && !rosterIds.isEmpty()) {
            List<ObjectId> objectIds = rosterIds.stream()
                    .filter(ObjectId::isValid)
                    .map(ObjectId::new)
                    .collect(Collectors.toList());
            model.addAttribute("champions", championRepository.findAllById(objectIds));
        } else {
            model.addAttribute("champions", List.of());
        }

        List<SiegeConditionEntity> activeEntities = siegeConditionService.findAllConditions().stream()
                .filter(SiegeConditionEntity::isActive)
                .toList();

        List<ConditionDropdownItem> dropdownItems = new ArrayList<>();

        for (SiegeConditionEntity entity : activeEntities) {
            String readableName = resolveEnumName(entity);
            String label = entity.getCategory().getDisplayName() + ": " + readableName;

            dropdownItems.add(new ConditionDropdownItem(
                    entity.getId().toHexString(),
                    label,
                    entity.getCategory().name(),
                    entity.getConditionKey()
            ));
        }

        model.addAttribute("siegeConditions", dropdownItems);

        Team formTeam = new Team();
        if (editTeamId != null && activeMember.getKnownTeams() != null) {
            formTeam = activeMember.getKnownTeams().stream()
                    .filter(t -> t.getId().equals(editTeamId))
                    .findFirst()
                    .orElse(new Team());
        }

        model.addAttribute("newTeam", formTeam);

        return "team-builder";
    }

    @PostMapping("/save")
    @PreAuthorize("hasRole('MEMBER')")
    public String saveTeam(@ModelAttribute Team team,
                           @RequestParam(required = false) String targetMemberId,
                           Authentication authentication, HttpSession session, RedirectAttributes redirectAttributes) {
        try {
            // Updated service call to include targetMemberId
            clanmemberService.saveKnownTeam(session, authentication, team, targetMemberId);
            redirectAttributes.addFlashAttribute("successMessage", "Team saved successfully!");

            if (StringUtils.hasText(targetMemberId)) {
                return "redirect:/profile/" + targetMemberId;
            }
            return "redirect:/profile";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error saving team: " + e.getMessage());
            if (StringUtils.hasText(targetMemberId)) {
                return "redirect:/teams/builder?targetMemberId=" + targetMemberId;
            }
            return "redirect:/teams/builder";
        }
    }

    @PostMapping("/delete/{teamId}")
    @PreAuthorize("hasRole('MEMBER')")
    public String deleteTeam(@PathVariable String teamId,
                             @RequestParam(required = false) String targetMemberId,
                             Authentication authentication, HttpSession session, RedirectAttributes redirectAttributes) {
        try {
            // Updated service call to include targetMemberId
            clanmemberService.deleteKnownTeam(session, authentication, teamId, targetMemberId);
            redirectAttributes.addFlashAttribute("successMessage", "Team deleted successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error deleting team: " + e.getMessage());
        }

        if (StringUtils.hasText(targetMemberId)) {
            return "redirect:/profile/" + targetMemberId;
        }
        return "redirect:/profile";
    }

    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(ObjectId.class, new PropertyEditorSupport() {
            @Override
            public void setAsText(String text) throws IllegalArgumentException {
                if (StringUtils.hasText(text)) {
                    setValue(new ObjectId(text));
                } else {
                    setValue(null);
                }
            }
        });
    }

    private String resolveEnumName(SiegeConditionEntity entity) {
        try {
            Class<? extends Enum<?>> enumClass = entity.getCategory().getEnumClass();
            for (Enum<?> constant : enumClass.getEnumConstants()) {
                if (constant.name().equals(entity.getConditionKey())) {
                    Method getNameMethod = constant.getClass().getMethod("getName");
                    return (String) getNameMethod.invoke(constant);
                }
            }
        } catch (Exception e) {
            return entity.getConditionKey();
        }
        return entity.getConditionKey();
    }

    private boolean canManageOthers(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_COORDINATOR") || a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_OWNER"));
    }
}