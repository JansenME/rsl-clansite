package com.rsl.clansite.controller;

import com.rsl.clansite.model.CompleteChampionsFilter;
import com.rsl.clansite.model.entity.ChampionEntity;
import com.rsl.clansite.model.enums.Affinity;
import com.rsl.clansite.model.enums.Alliance;
import com.rsl.clansite.model.enums.Faction;
import com.rsl.clansite.model.enums.Rarity;
import com.rsl.clansite.model.enums.Type;
import com.rsl.clansite.service.ChampionsService;
import com.rsl.clansite.service.CommonsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/champions")
public class ChampionsController {
    private final CommonsService commonsService;
    private final ChampionsService championsService;

    @Autowired
    public ChampionsController(final CommonsService commonsService, final ChampionsService championsService) {
        this.commonsService = commonsService;
        this.championsService = championsService;
    }

    @GetMapping(value={"", "/"})
    public String getAllChampions(Model model) {
        fillModel(model);

        model.addAttribute("filtersWrapper", new CompleteChampionsFilter());
        model.addAttribute("champions", championsService.getAllChampions());

        return "champions";
    }

    @GetMapping("/saveChampsFromCsv")
    public ResponseEntity<List<ChampionEntity>> saveAllChampionsFromCsv() {
        return ResponseEntity.of(Optional.of(championsService.saveAllChampionsFromCsv()));
    }

    private void fillModel(Model model) {
        commonsService.fillModel(model);

        int totalAmountOfChampions = 299+238+265+174;

        model.addAttribute("rarities", Arrays.stream(Rarity.values()).toList());
        model.addAttribute("types", Arrays.stream(Type.values()).toList());
        model.addAttribute("affinities", Arrays.stream(Affinity.values()).toList());
        model.addAttribute("factions", Arrays.stream(Faction.values()).toList());
        model.addAttribute("alliances", Arrays.stream(Alliance.values()).toList());

        model.addAttribute("amountOfChampions", totalAmountOfChampions);
        model.addAttribute("championsToGo", totalAmountOfChampions - championsService.getAllChampions().size());
    }
}
