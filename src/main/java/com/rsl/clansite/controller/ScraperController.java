package com.rsl.clansite.controller;

import com.rsl.clansite.model.DashboardRow;
import com.rsl.clansite.model.entity.ChampionEntity;
import com.rsl.clansite.model.enums.Alliance;
import com.rsl.clansite.model.enums.Faction;
import com.rsl.clansite.model.enums.Rarity;
import com.rsl.clansite.repository.ChampionRepository;
import com.rsl.clansite.service.CommonsService;
import com.rsl.clansite.service.HellHadesScraperService;
import com.rsl.clansite.service.TargetService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Controller
@RequestMapping("/admin")
public class ScraperController {
    private final HellHadesScraperService scraperService;
    private final TargetService targetService;
    private final ChampionRepository championRepository;
    private final CommonsService commonsService;

    @Value("${app.storage.location.champion-cards}")
    private String imageStorageLocation;

    public ScraperController(HellHadesScraperService scraperService, TargetService targetService, ChampionRepository championRepository, CommonsService commonsService) {
        this.scraperService = scraperService;
        this.targetService = targetService;
        this.championRepository = championRepository;
        this.commonsService = commonsService;
    }

    @GetMapping("/scraper")
    @PreAuthorize("hasRole('ADMIN')")
    public String scraperDashboard(Model model, Authentication authentication) {
        commonsService.fillModel(model, authentication);

        List<ChampionEntity> allChampions = championRepository.findAll();
        List<AllianceGroup> allianceGroups = new ArrayList<>();

        for (Alliance alliance : Alliance.values()) {
            List<DashboardRow> allianceRows = new ArrayList<>();
            int allianceTotalDb = 0;
            int allianceTotalTargets = 0;

            for (Faction faction : Faction.values()) {
                if (faction.getAlliance() == alliance) {
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
                    allianceRows.add(row);
                    allianceTotalDb += row.getDatabase().values().stream().mapToInt(Integer::intValue).sum();
                    allianceTotalTargets += myTotal;
                }
            }
            if (!allianceRows.isEmpty()) {
                allianceGroups.add(new AllianceGroup(alliance, allianceRows, allianceTotalDb, allianceTotalTargets));
            }
        }

        model.addAttribute("allianceGroups", allianceGroups);
        model.addAttribute("rarities", Rarity.values());
        return "scraper-dashboard";
    }

    @PostMapping("/scraper/targets/update")
    @PreAuthorize("hasRole('ADMIN')")
    public String updateFactionTargets(@RequestParam Map<String, String> allParams,
                                       RedirectAttributes redirectAttributes,
                                       Authentication authentication) {
        int updatedCount = 0;
        log.info("Received Target Update Request with {} params", allParams.size());

        for (Map.Entry<String, String> entry : allParams.entrySet()) {
            if (entry.getKey().contains("_")) {
                try {
                    // Expect format "Faction Name_RARITY"
                    String[] parts = entry.getKey().split("_");
                    if (parts.length != 2) continue;

                    String factionName = parts[0].trim();
                    String rarityName = parts[1].trim();

                    Faction faction = Faction.getFactionByName(factionName);
                    if (faction == null) {
                        faction = Faction.getFactionByName(factionName.replace("_", " "));
                    }

                    if (faction == null) {
                        log.warn("Could not find faction for key: {}", factionName);
                        continue;
                    }

                    Rarity rarity = Rarity.valueOf(rarityName);
                    int count = Integer.parseInt(entry.getValue());
                    int current = targetService.getTargetCount(faction, rarity);

                    if (current != count) {
                        targetService.updateTarget(faction, rarity, count, authentication);
                        updatedCount++;
                    }
                } catch (Exception e) {
                    log.error("Failed to parse update for key {}: {}", entry.getKey(), e.getMessage());
                }
            }
        }

        if (updatedCount > 0) {
            redirectAttributes.addFlashAttribute("message", "Successfully updated targets for " + updatedCount + " faction/rarity combinations.");
            redirectAttributes.addFlashAttribute("alertClass", "alert-success");
        } else {
            redirectAttributes.addFlashAttribute("message", "No changes detected.");
            redirectAttributes.addFlashAttribute("alertClass", "alert-info");
        }

        return "redirect:/admin/scraper";
    }

    @PostMapping("/scraper/faction/{factionName}/execute")
    @PreAuthorize("hasRole('ADMIN')")
    public String executeScrape(@PathVariable String factionName,
                                @org.springframework.web.bind.annotation.RequestHeader(value = "Referer", required = false) String referer,
                                Authentication authentication,
                                RedirectAttributes redirectAttributes) {

        Faction faction = Faction.getFactionByName(factionName.replace("-", " "));
        if (faction == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Invalid Faction Name.");
            return "redirect:/admin/scraper";
        }

        boolean forceRefresh = (referer != null && referer.contains("data-health"));
        var contexts = scraperService.scanForChampions(faction, forceRefresh);

        if (contexts.isEmpty()) {
            redirectAttributes.addFlashAttribute("message", "No champions found for " + faction.getName());
            redirectAttributes.addFlashAttribute("alertClass", "alert-info");
        } else {
            scraperService.importChampions(contexts, faction, authentication);
            String actionType = forceRefresh ? "Refreshed/Updated" : "imported";
            redirectAttributes.addFlashAttribute("message", "Successfully " + actionType + " " + contexts.size() + " champions into " + faction.getName());
            redirectAttributes.addFlashAttribute("alertClass", "alert-success");
        }

        if (forceRefresh) {
            return "redirect:/admin/data-health";
        }
        return "redirect:/admin/scraper";
    }

    @GetMapping("/data-health")
    @PreAuthorize("hasRole('ADMIN')")
    public String showDataHealth(Model model, Authentication authentication) {
        commonsService.fillModel(model, authentication);
        List<ChampionEntity> allChampions = championRepository.findAll();
        List<ProblemRow> problems = new ArrayList<>();

        for (ChampionEntity c : allChampions) {
            List<String> issues = new ArrayList<>();
            if (c.getType() == null) issues.add("Missing Type");
            if (c.getAffinity() == null) issues.add("Missing Affinity");
            if (c.getRarity() == null) issues.add("Missing Rarity");
            if (c.getFaction() == null) issues.add("Missing Faction");
            if (c.getBaseStats() == null) issues.add("Missing Stats");
            if (c.getImagename() == null || c.getImagename().isEmpty()) {
                issues.add("No Image Name in DB");
            } else {
                Path path = Paths.get(imageStorageLocation, c.getImagename());
                if (!Files.exists(path)) issues.add("File Missing on Disk");
            }
            if (!issues.isEmpty()) problems.add(new ProblemRow(c, issues));
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
        private int totalTargets;
    }
}