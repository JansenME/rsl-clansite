package com.rsl.clansite.controller;

import com.rsl.clansite.exceptions.ChampionSaveException;
import com.rsl.clansite.model.CompleteChampionsFilter;
import com.rsl.clansite.model.dto.ChampionEntryDTO;
import com.rsl.clansite.model.dto.DataHealthDTO;
import com.rsl.clansite.model.entity.ClanmemberEntity;
import com.rsl.clansite.model.enums.Affinity;
import com.rsl.clansite.model.enums.Alliance;
import com.rsl.clansite.model.enums.AuraLocation;
import com.rsl.clansite.model.enums.AuraStat;
import com.rsl.clansite.model.enums.Faction;
import com.rsl.clansite.model.enums.Rarity;
import com.rsl.clansite.model.enums.Type;
import com.rsl.clansite.repository.ChampionRepository;
import com.rsl.clansite.service.ChampionsService;
import com.rsl.clansite.service.ClanmemberService;
import com.rsl.clansite.service.CommonsService;
import com.rsl.clansite.service.RosterService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/champions")
public class ChampionsController {
    private final CommonsService commonsService;
    private final ChampionsService championsService;
    private final ClanmemberService clanmemberService;
    private final RosterService rosterService;
    private final ChampionRepository championRepository;

    public ChampionsController(final CommonsService commonsService,
                               final ChampionsService championsService,
                               final ClanmemberService clanmemberService,
                               final RosterService rosterService,
                               final ChampionRepository championRepository) {
        this.commonsService = commonsService;
        this.championsService = championsService;
        this.clanmemberService = clanmemberService;
        this.rosterService = rosterService;
        this.championRepository = championRepository;
    }

    @GetMapping(value={"", "/"})
    public String getAllChampions(@RequestParam(required = false) String editingMemberId,
                                  Model model,
                                  Authentication authentication,
                                  HttpSession session) {
        fillModel(model, authentication, session);
        ClanmemberEntity activeMember = clanmemberService.getActiveClanmember(session, authentication);

        ClanmemberEntity targetMember = activeMember;
        boolean isManagementMode = false;
        boolean autoOpenEditMode = false;

        if (editingMemberId != null && authentication != null) {
            if (activeMember.getId().toHexString().equals(editingMemberId)) {
                autoOpenEditMode = true;
            } else {
                boolean isCoordinator = authentication.getAuthorities().stream()
                        .anyMatch(a -> a.getAuthority().equals("ROLE_COORDINATOR")
                                || a.getAuthority().equals("ROLE_ADMIN")
                                || a.getAuthority().equals("ROLE_OWNER"));

                if (isCoordinator) {
                    try {
                        targetMember = clanmemberService.getMemberById(editingMemberId);
                        if (!targetMember.getId().equals(activeMember.getId())) {
                            isManagementMode = true;
                            autoOpenEditMode = true;
                        } else {
                            autoOpenEditMode = true;
                        }
                    } catch (Exception e) {
                        model.addAttribute("errorMessage", "Could not find requested member. Showing your own roster instead.");
                    }
                } else {
                    model.addAttribute("errorMessage", "You do not have permission to manage other members' rosters.");
                }
            }
        }

        if (targetMember != null) {
            model.addAttribute("ownedChampionIds", targetMember.getRosterChampionIds());
            model.addAttribute("targetMember", targetMember);
        }

        model.addAttribute("activeMember", activeMember);
        model.addAttribute("isManagementMode", isManagementMode);
        model.addAttribute("autoOpenEditMode", autoOpenEditMode);

        model.addAttribute("filtersWrapper", new CompleteChampionsFilter());
        model.addAttribute("champions", championsService.getAllChampions());

        return "champions";
    }

    @PostMapping("/roster-save")
    @PreAuthorize("isAuthenticated()")
    public String saveRoster(@RequestParam("targetMemberId") String targetMemberId,
                             @RequestParam(value = "championIds", required = false) List<String> championIds,
                             Authentication authentication,
                             RedirectAttributes redirectAttributes) {
        try {
            rosterService.updateRoster(targetMemberId, championIds, authentication);
            redirectAttributes.addFlashAttribute("successMessage", "Roster updated successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to save roster: " + e.getMessage());
        }

        return "redirect:/champions";
    }

    @GetMapping("/new")
    @PreAuthorize("hasRole('OWNER')")
    public String newChampionForm(Model model, Authentication authentication, HttpSession session) {
        fillModel(model, authentication, session);
        model.addAttribute("newChampion", new ChampionEntryDTO(true));
        return "champion-entry";
    }

    @PostMapping("/save")
    @PreAuthorize("hasRole('OWNER')")
    public String saveChampion(
            @ModelAttribute("newChampion") @Valid ChampionEntryDTO dto,
            BindingResult bindingResult,
            Model model,
            Authentication authentication,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            fillModel(model, authentication, session);
            model.addAttribute("errorMessage", "Please correct the validation errors below.");
            return "champion-entry";
        }

        try {
            championsService.saveNewChampion(dto, authentication);
            redirectAttributes.addFlashAttribute("successMessage", "Champion '" + dto.getName() + "' saved to Database!");
        } catch (ChampionSaveException e) {
            if (e.getMessage().contains("already taken")) {
                bindingResult.rejectValue("name", "error.newChampion", e.getMessage());
            }

            fillModel(model, authentication, session);
            model.addAttribute("errorMessage", e.getMessage());
            return "champion-entry";
        }

        return "redirect:/champions";
    }

    @GetMapping("/{id}")
    public String viewChampionDetails(@PathVariable String id, Model model, Authentication authentication, HttpSession session) {
        commonsService.fillModel(model, authentication, session);
        model.addAttribute("champion", championsService.getChampionById(id));
        return "champion-details";
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasRole('OWNER')")
    public String editChampionForm(@PathVariable String id, Model model, Authentication authentication, HttpSession session) {
        fillModel(model, authentication, session);
        model.addAttribute("newChampion", championsService.getChampionForEdit(id));
        model.addAttribute("isEditMode", true);
        return "champion-entry";
    }

    @PostMapping("/{id}/edit")
    @PreAuthorize("hasRole('OWNER')")
    public String updateChampion(
            @PathVariable String id,
            @ModelAttribute("newChampion") @Valid ChampionEntryDTO dto,
            BindingResult bindingResult,
            Model model,
            Authentication authentication,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            fillModel(model, authentication, session);
            model.addAttribute("isEditMode", true);
            model.addAttribute("errorMessage", "Please correct the validation errors below.");
            return "champion-entry";
        }

        try {
            championsService.updateChampion(id, dto, authentication);
            redirectAttributes.addFlashAttribute("successMessage", "Champion '" + dto.getName() + "' updated successfully!");
        } catch (ChampionSaveException e) {
            fillModel(model, authentication, session);
            model.addAttribute("isEditMode", true);
            model.addAttribute("errorMessage", e.getMessage());
            return "champion-entry";
        }

        return "redirect:/champions/" + id;
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasRole('OWNER')")
    public String deleteChampion(@PathVariable String id, Authentication authentication, RedirectAttributes redirectAttributes) {
        championsService.deleteChampion(id, authentication);
        redirectAttributes.addFlashAttribute("successMessage", "Champion deleted successfully.");
        return "redirect:/champions";
    }

    private void fillModel(Model model, Authentication authentication, HttpSession session) {
        commonsService.fillModel(model, authentication, session);

        DataHealthDTO health = championsService.getDataHealth();
        model.addAttribute("dataHealth", health);

        model.addAttribute("rarities", Rarity.values());
        model.addAttribute("types", Type.values());
        model.addAttribute("affinities", Affinity.values());
        model.addAttribute("factions", Faction.values());
        model.addAttribute("alliances", Alliance.values());
        model.addAttribute("auraStats", AuraStat.values());
        model.addAttribute("auraLocations", AuraLocation.values());

        long totalAmountOfChampions = championRepository.count();
        model.addAttribute("amountOfChampions", totalAmountOfChampions);

        model.addAttribute("championsToGo", health.totalMissing());
    }
}