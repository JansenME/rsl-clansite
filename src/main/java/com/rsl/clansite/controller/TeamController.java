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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

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

    @GetMapping("/builder")
    @PreAuthorize("hasRole('MEMBER')")
    public String builder(Model model, Authentication authentication, HttpSession session) {
        commonsService.fillModel(model, authentication);

        ClanmemberEntity activeMember = clanmemberService.getActiveClanmember(session, authentication);
        if (activeMember == null) {
            return "redirect:/";
        }

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

        List<SiegeConditionEntity> activeConditions = siegeConditionService.findAllConditions().stream()
                .filter(SiegeConditionEntity::isActive)
                .collect(Collectors.toList());
        model.addAttribute("siegeConditions", activeConditions);

        model.addAttribute("newTeam", new Team());

        return "team-builder";
    }

    @PostMapping("/save")
    @PreAuthorize("hasRole('MEMBER')")
    public String saveTeam(@ModelAttribute Team team, Authentication authentication, HttpSession session, RedirectAttributes redirectAttributes) {
        try {
            clanmemberService.addKnownTeam(session, authentication, team);
            redirectAttributes.addFlashAttribute("successMessage", "Team saved successfully!");
            return "redirect:/profile";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error saving team: " + e.getMessage());
            return "redirect:/teams/builder";
        }
    }
}