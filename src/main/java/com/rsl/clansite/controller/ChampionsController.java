package com.rsl.clansite.controller;

import com.rsl.clansite.exceptions.ChampionSaveException;
import com.rsl.clansite.model.CompleteChampionsFilter;
import com.rsl.clansite.model.dto.ChampionEntryDTO;
import com.rsl.clansite.model.entity.ClanmemberEntity;
import com.rsl.clansite.model.enums.Affinity;
import com.rsl.clansite.model.enums.Alliance;
import com.rsl.clansite.model.enums.AuraLocation;
import com.rsl.clansite.model.enums.AuraStat;
import com.rsl.clansite.model.enums.Faction;
import com.rsl.clansite.model.enums.Rarity;
import com.rsl.clansite.model.enums.Type;
import com.rsl.clansite.service.ChampionsService;
import com.rsl.clansite.service.ClanmemberService;
import com.rsl.clansite.service.CommonsService;
import com.rsl.clansite.service.RosterService;
import com.rsl.clansite.service.TargetService;
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
    private final TargetService targetService;

    public ChampionsController(final CommonsService commonsService,
                               final ChampionsService championsService,
                               final ClanmemberService clanmemberService,
                               final RosterService rosterService,
                               final TargetService targetService) {
        this.commonsService = commonsService;
        this.championsService = championsService;
        this.clanmemberService = clanmemberService;
        this.rosterService = rosterService;
        this.targetService = targetService;
    }

    @GetMapping(value={"", "/"})
    public String getAllChampions(Model model, Authentication authentication, HttpSession session) {
        fillModel(model, authentication, session);

        // Fetch the Active Member so we know which checkboxes to tick
        ClanmemberEntity activeMember = clanmemberService.getActiveClanmember(session, authentication);
        if (activeMember != null) {
            model.addAttribute("ownedChampionIds", activeMember.getRosterChampionIds());
            model.addAttribute("activeMember", activeMember); // For the sticky footer name
        }

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

        // --- FIXED: Use Dynamic JSON data instead of Hardcode ---
        int totalAmountOfChampions = targetService.getTotalChampionCount();

        model.addAttribute("rarities", Rarity.values());
        model.addAttribute("types", Type.values());
        model.addAttribute("affinities", Affinity.values());
        model.addAttribute("factions", Faction.values());
        model.addAttribute("alliances", Alliance.values());
        model.addAttribute("auraStats", AuraStat.values());
        model.addAttribute("auraLocations", AuraLocation.values());

        model.addAttribute("amountOfChampions", totalAmountOfChampions);
        model.addAttribute("championsToGo", totalAmountOfChampions - championsService.getAllChampions().size());
    }
}