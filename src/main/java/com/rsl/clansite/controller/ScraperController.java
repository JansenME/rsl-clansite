package com.rsl.clansite.controller;

import com.rsl.clansite.model.DashboardRow;
import com.rsl.clansite.model.entity.ChampionEntity;
import com.rsl.clansite.model.enums.Faction;
import com.rsl.clansite.model.enums.Rarity;
import com.rsl.clansite.repository.ChampionRepository;
import com.rsl.clansite.service.HellHadesScraperService;
import com.rsl.clansite.service.TargetService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin/scraper")
public class ScraperController {

    private final HellHadesScraperService scraperService;
    private final TargetService targetService;
    private final ChampionRepository championRepository;

    public ScraperController(HellHadesScraperService scraperService, TargetService targetService, ChampionRepository championRepository) {
        this.scraperService = scraperService;
        this.targetService = targetService;
        this.championRepository = championRepository;
    }

    @GetMapping("")
    @PreAuthorize("hasRole('OWNER')")
    public String scraperDashboard(Model model) {
        List<DashboardRow> dashboardRows = new ArrayList<>();

        // 1. Fetch all champions once to minimize DB calls
        List<ChampionEntity> allChampions = championRepository.findAll();

        for (Faction faction : Faction.values()) {
            // A. My Targets (Json)
            Map<Rarity, Integer> targets = new HashMap<>();
            int myTotal = targetService.getMyTotalForFaction(faction);

            // B. Database Counts (Mongo)
            Map<Rarity, Integer> database = new HashMap<>();

            // Filter champions for this faction
            List<ChampionEntity> factionChampions = allChampions.stream()
                    .filter(c -> c.getFaction() == faction)
                    .toList();

            for (Rarity r : Rarity.values()) {
                targets.put(r, targetService.getTargetCount(faction, r));

                // Count how many we have in DB for this Rarity
                int count = (int) factionChampions.stream()
                        .filter(c -> c.getRarity() == r)
                        .count();
                database.put(r, count);
            }

            // C. HellHades Data (Online Live Scan) - Needed for button logic
            Map<Rarity, Integer> online = scraperService.getOnlineCounts(faction);

            dashboardRows.add(new DashboardRow(faction, targets, database, online, myTotal));
        }

        model.addAttribute("dashboardRows", dashboardRows);
        model.addAttribute("rarities", Rarity.values());

        return "scraper-dashboard";
    }

    @PostMapping("/faction/{factionName}/execute")
    @PreAuthorize("hasRole('OWNER')")
    public String executeScrape(@PathVariable String factionName, Authentication authentication, RedirectAttributes redirectAttributes) {
        Faction faction = Faction.getFactionByName(factionName.replace("-", " "));
        if (faction == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Invalid Faction Name.");
            return "redirect:/admin/scraper";
        }

        // 1. Scan
        var newContexts = scraperService.scanForNewChampions(faction);

        if (newContexts.isEmpty()) {
            redirectAttributes.addFlashAttribute("message", "No new champions found for " + faction.getName());
            redirectAttributes.addFlashAttribute("alertClass", "alert-info");
        } else {
            // 2. Import immediately
            scraperService.importChampions(newContexts, faction, authentication);
            redirectAttributes.addFlashAttribute("message", "Successfully imported " + newContexts.size() + " champions into " + faction.getName());
            redirectAttributes.addFlashAttribute("alertClass", "alert-success");
        }

        return "redirect:/admin/scraper";
    }
}