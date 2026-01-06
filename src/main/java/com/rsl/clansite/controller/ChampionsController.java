package com.rsl.clansite.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rsl.clansite.exceptions.ChampionSaveException;
import com.rsl.clansite.model.CompleteChampionsFilter;
import com.rsl.clansite.model.dto.ChampionEntryDTO;
import com.rsl.clansite.model.entity.ChampionEntity;
import com.rsl.clansite.model.enums.*;
import com.rsl.clansite.service.ChampionsService;
import com.rsl.clansite.service.CommonsService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

import java.util.List;
import java.util.Optional;

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
            championsService.saveNewChampion(dto);
            redirectAttributes.addFlashAttribute("successMessage", "Champion '" + dto.getName() + "' saved to Database!");
        } catch (ChampionSaveException e) {
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