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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin")
public class ScraperController {

    private final HellHadesScraperService scraperService;
    private final TargetService targetService;
    private final ChampionRepository championRepository;

    public ScraperController(HellHadesScraperService scraperService, TargetService targetService, ChampionRepository championRepository) {
        this.scraperService = scraperService;
        this.targetService = targetService;
        this.championRepository = championRepository;
    }

    @GetMapping("/scraper")
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

    @PostMapping("/scraper/faction/{factionName}/execute")
    @PreAuthorize("hasRole('OWNER')")
    public String executeScrape(@PathVariable String factionName,
                                @org.springframework.web.bind.annotation.RequestHeader(value = "Referer", required = false) String referer,
                                Authentication authentication,
                                RedirectAttributes redirectAttributes) {

        Faction faction = Faction.getFactionByName(factionName.replace("-", " "));
        if (faction == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Invalid Faction Name.");
            return "redirect:/admin/scraper";
        }

        // 1. Determine Strategy based on where the user clicked
        // If coming from "data-health", we are fixing data -> Force Refresh
        boolean forceRefresh = (referer != null && referer.contains("data-health"));

        // 2. Scan (using the new service signature)
        var contexts = scraperService.scanForChampions(faction, forceRefresh);

        if (contexts.isEmpty()) {
            redirectAttributes.addFlashAttribute("message", "No champions found for " + faction.getName());
            redirectAttributes.addFlashAttribute("alertClass", "alert-info");
        } else {
            // 3. Import (Upsert logic in service handles updates vs inserts)
            scraperService.importChampions(contexts, faction, authentication);

            String actionType = forceRefresh ? "Refreshed/Updated" : "imported";
            redirectAttributes.addFlashAttribute("message", "Successfully " + actionType + " " + contexts.size() + " champions into " + faction.getName());
            redirectAttributes.addFlashAttribute("alertClass", "alert-success");
        }

        // 4. Smart Redirect
        if (forceRefresh) {
            return "redirect:/admin/data-health";
        }

        return "redirect:/admin/scraper";
    }

    @GetMapping("/data-health")
    @PreAuthorize("hasRole('OWNER')")
    public String showDataHealth(Model model) {
        List<ChampionEntity> allChampions = championRepository.findAll();
        List<ProblemRow> problems = new ArrayList<>();

        String imageBasePath = "src/main/resources/static/images/champions/";

        for (ChampionEntity c : allChampions) {
            List<String> issues = new ArrayList<>();

            // 1. Check Data Integrity
            if (c.getType() == null) issues.add("Missing Type");
            if (c.getAffinity() == null) issues.add("Missing Affinity");
            if (c.getRarity() == null) issues.add("Missing Rarity");
            if (c.getFaction() == null) issues.add("Missing Faction");
            if (c.getBaseStats() == null) issues.add("Missing Stats");

            // 2. Check Image Reference
            if (c.getImagename() == null || c.getImagename().isEmpty()) {
                issues.add("No Image Name in DB");
            } else {
                // 3. Check Physical File
                Path path = Paths.get(imageBasePath + c.getImagename());
                if (!Files.exists(path)) {
                    issues.add("File Missing on Disk");
                }
            }

            // Only add to list if there are actual issues
            if (!issues.isEmpty()) {
                problems.add(new ProblemRow(c, issues));
            }
        }

        model.addAttribute("problems", problems);
        return "data-health";
    }

    @Data
    @AllArgsConstructor
    public static class ProblemRow {
        private ChampionEntity champion;
        private List<String> issues;
    }

    @Data
    @AllArgsConstructor
    public static class AllianceGroup {
        private Alliance alliance;
        private List<DashboardRow> rows;
        private int totalChampions;
    }
}