package com.rsl.clansite.controller;

import com.rsl.clansite.model.DashboardRow;
import com.rsl.clansite.model.entity.ChampionEntity;
import com.rsl.clansite.model.enums.Alliance;
import com.rsl.clansite.model.enums.Faction;
import com.rsl.clansite.model.enums.Rarity;
import com.rsl.clansite.repository.ChampionRepository;
import com.rsl.clansite.service.HellHadesScraperService;
import com.rsl.clansite.service.TargetService;
import lombok.AllArgsConstructor;
import lombok.Data;
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
        // 1. Fetch all champions once
        List<ChampionEntity> allChampions = championRepository.findAll();

        // 2. Create a list to hold our Groups
        List<AllianceGroup> allianceGroups = new ArrayList<>();

        // 3. Iterate over Alliances (Telerian, Gaellen, etc.)
        for (Alliance alliance : Alliance.values()) {
            List<DashboardRow> allianceRows = new ArrayList<>();
            int allianceTotalDb = 0; // Counter for the header

            // Find factions that belong to this alliance
            for (Faction faction : Faction.values()) {
                if (faction.getAlliance() == alliance) {

                    // --- EXISTING LOGIC FOR ROW CREATION ---
                    Map<Rarity, Integer> targets = new HashMap<>();
                    int myTotal = targetService.getMyTotalForFaction(faction);
                    Map<Rarity, Integer> database = new HashMap<>();

                    List<ChampionEntity> factionChampions = allChampions.stream()
                            .filter(c -> c.getFaction() == faction)
                            .toList();

                    for (Rarity r : Rarity.values()) {
                        targets.put(r, targetService.getTargetCount(faction, r));
                        int count = (int) factionChampions.stream().filter(c -> c.getRarity() == r).count();
                        database.put(r, count);
                    }

                    Map<Rarity, Integer> online = scraperService.getOnlineCounts(faction);
                    DashboardRow row = new DashboardRow(faction, targets, database, online, myTotal);
                    // ---------------------------------------

                    allianceRows.add(row);

                    // Sum up for the Alliance Header
                    allianceTotalDb += row.getDatabase().values().stream().mapToInt(Integer::intValue).sum();
                }
            }

            // Only add the group if it has factions (which they all do)
            if (!allianceRows.isEmpty()) {
                allianceGroups.add(new AllianceGroup(alliance, allianceRows, allianceTotalDb));
            }
        }

        model.addAttribute("allianceGroups", allianceGroups); // Send groups, not raw rows
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

    @Data
    @AllArgsConstructor
    public static class AllianceGroup {
        private Alliance alliance;
        private List<DashboardRow> rows;
        private int totalChampions;
    }
}