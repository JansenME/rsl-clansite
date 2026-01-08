package com.rsl.clansite.controller;

import com.rsl.clansite.model.enums.Faction;
import com.rsl.clansite.service.HellHadesScraperService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/admin/scraper")
public class ScraperController {

    private final HellHadesScraperService scraperService;

    public ScraperController(HellHadesScraperService scraperService) {
        this.scraperService = scraperService;
    }

    @GetMapping("")
    @PreAuthorize("hasRole('OWNER')")
    public String scraperDashboard(Model model) {
        model.addAttribute("factions", Faction.values());
        return "scraper-dashboard";
    }

    @GetMapping("/faction/{factionName}")
    @PreAuthorize("hasRole('OWNER')")
    public String previewScrape(@PathVariable String factionName, Model model, RedirectAttributes redirectAttributes) {
        Faction faction = Faction.getFactionByName(factionName.replace("-", " "));
        if (faction == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Invalid Faction Name.");
            return "redirect:/admin/scraper";
        }

        var newContexts = scraperService.scanForNewChampions(faction);

        model.addAttribute("faction", faction);
        model.addAttribute("newChampionCount", newContexts.size());
        return "scraper-preview";
    }

    @PostMapping("/faction/{factionName}/execute")
    @PreAuthorize("hasRole('OWNER')")
    public String executeScrape(@PathVariable String factionName, Authentication authentication, RedirectAttributes redirectAttributes) {
        Faction faction = Faction.getFactionByName(factionName.replace("-", " "));
        if (faction == null) {
            return "redirect:/admin/scraper";
        }

        var newContexts = scraperService.scanForNewChampions(faction);

        if (newContexts.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "No new champions found to import.");
            return "redirect:/admin/scraper";
        }

        scraperService.importChampions(newContexts, faction, authentication);

        redirectAttributes.addFlashAttribute("successMessage", "Successfully imported " + newContexts.size() + " champions into " + faction.getName());
        return "redirect:/champions";
    }
}