package com.rsl.clansite.controller;

import com.rsl.clansite.model.SiegeStructure;
import com.rsl.clansite.model.entity.ClanmemberEntity;
import com.rsl.clansite.model.entity.SiegeEntity;
import com.rsl.clansite.model.enums.ClanGroup;
import com.rsl.clansite.repository.SiegeRepository;
import com.rsl.clansite.service.ClanmemberService;
import com.rsl.clansite.service.CommonsService;
import com.rsl.clansite.service.SiegeService;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Controller
@RequestMapping("/siege")
public class SiegeController {

    private final CommonsService commonsService;
    private final ClanmemberService clanmemberService;
    private final SiegeService siegeService;
    private final SiegeRepository siegeRepository; // Direct access for updates

    public SiegeController(CommonsService commonsService,
                           ClanmemberService clanmemberService,
                           SiegeService siegeService,
                           SiegeRepository siegeRepository) {
        this.commonsService = commonsService;
        this.clanmemberService = clanmemberService;
        this.siegeService = siegeService;
        this.siegeRepository = siegeRepository;
    }

    @GetMapping
    @PreAuthorize("hasRole('MEMBER')")
    public String siegeDashboard(Model model, Authentication authentication, HttpSession session) {
        commonsService.fillModel(model, authentication, session);
        ClanmemberEntity activeMember = clanmemberService.getActiveClanmember(session, authentication);

        ClanGroup primaryGroup = activeMember.getClanGroup();
        if (primaryGroup == null) {
            primaryGroup = ClanGroup.T1;
        }

        Object switchedGroupObj = session.getAttribute("switchedClanGroup");
        if (switchedGroupObj != null) {
            try {
                primaryGroup = ClanGroup.valueOf(switchedGroupObj.toString());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid ClanGroup in session: {}", switchedGroupObj);
            }
        }

        ClanGroup secondaryGroup = (primaryGroup == ClanGroup.T1) ? ClanGroup.T2 : ClanGroup.T1;

        SiegeEntity primarySiege = getOrCreateSiege(primaryGroup);
        SiegeEntity secondarySiege = getOrCreateSiege(secondaryGroup);

        List<SiegeEntity> siegeList = new ArrayList<>();
        siegeList.add(primarySiege);
        siegeList.add(secondarySiege);

        model.addAttribute("siegeList", siegeList);
        model.addAttribute("primaryGroup", primaryGroup);

        return "siege-dashboard";
    }

    @PostMapping("/structure/update")
    @PreAuthorize("hasRole('COORDINATOR') or hasRole('ADMIN') or hasRole('OWNER')")
    public String updateStructureStatus(@RequestParam("siegeId") String siegeId,
                                        @RequestParam("structureId") String structureId,
                                        @RequestParam("mapType") String mapType, // "DEFENSE" or "TARGET"
                                        @RequestParam("isCleared") boolean isCleared) {

        Optional<SiegeEntity> siegeOpt = siegeRepository.findById(new ObjectId(siegeId));
        if (siegeOpt.isEmpty()) {
            return "redirect:/siege";
        }
        SiegeEntity siege = siegeOpt.get();

        List<SiegeStructure> targetList = mapType.equals("DEFENSE")
                ? siege.getDefensiveStructures()
                : siege.getTargetStructures();

        for (SiegeStructure structure : targetList) {
            if (structure.getId().equals(structureId)) {
                structure.setCleared(isCleared);
                break;
            }
        }

        siegeRepository.save(siege);
        return "redirect:/siege";
    }

    private SiegeEntity getOrCreateSiege(ClanGroup group) {
        return siegeService.getActiveSiege(group)
                .orElseGet(() -> {
                    log.info("Lazy-initializing first siege for {}", group);
                    return siegeService.createNextSiege(group);
                });
    }
}