package com.rsl.clansite.controller;

import com.rsl.clansite.exceptions.ChampionSaveException;
import com.rsl.clansite.model.CompleteChampionsFilter;
import com.rsl.clansite.model.dto.ChampionEntryDTO;
import com.rsl.clansite.model.enums.*;
import com.rsl.clansite.service.ChampionsService;
import com.rsl.clansite.service.CommonsService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/champions")
public class ChampionsController {
    private final CommonsService commonsService;
    private final ChampionsService championsService;

    public ChampionsController(final CommonsService commonsService, final ChampionsService championsService) {
        this.commonsService = commonsService;
        this.championsService = championsService;
    }

    @GetMapping(value={"", "/"})
    public String getAllChampions(Model model, Authentication authentication) {
        fillModel(model, authentication);

        model.addAttribute("filtersWrapper", new CompleteChampionsFilter());
        model.addAttribute("champions", championsService.getAllChampions());

        return "champions";
    }

    @GetMapping("/new")
    @PreAuthorize("hasRole('OWNER')")
    public String newChampionForm(Model model, Authentication authentication) {
        fillModel(model, authentication);
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
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            fillModel(model, authentication);
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

            fillModel(model, authentication);
            model.addAttribute("errorMessage", e.getMessage());
            return "champion-entry";
        }

        return "redirect:/champions";
    }

    @GetMapping("/{id}")
    public String viewChampionDetails(@PathVariable String id, Model model, Authentication authentication) {
        commonsService.fillModel(model, authentication);

        model.addAttribute("champion", championsService.getChampionById(id));

        return "champion-details";
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasRole('OWNER')")
    public String editChampionForm(@PathVariable String id, Model model, Authentication authentication) {
        fillModel(model, authentication);

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
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            fillModel(model, authentication);
            model.addAttribute("isEditMode", true);
            model.addAttribute("errorMessage", "Please correct the validation errors below.");
            return "champion-entry";
        }

        try {
            championsService.updateChampion(id, dto, authentication);
            redirectAttributes.addFlashAttribute("successMessage", "Champion '" + dto.getName() + "' updated successfully!");
        } catch (ChampionSaveException e) {
            fillModel(model, authentication);
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

    private void fillModel(Model model, Authentication authentication) {
        commonsService.fillModel(model, authentication);

        int totalAmountOfChampions = 301+239+266+177;

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