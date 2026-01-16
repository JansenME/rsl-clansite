package com.rsl.clansite.controller;

import com.rsl.clansite.model.entity.ClanmemberEntity;
import com.rsl.clansite.model.entity.SiegeEntity;
import com.rsl.clansite.model.enums.ClanGroup;
import com.rsl.clansite.service.ClanmemberService;
import com.rsl.clansite.service.CommonsService;
import com.rsl.clansite.service.SiegeService;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Controller
@RequestMapping("/siege")
public class SiegeController {

    private final CommonsService commonsService;
    private final ClanmemberService clanmemberService;
    private final SiegeService siegeService;

    public SiegeController(CommonsService commonsService, ClanmemberService clanmemberService, SiegeService siegeService) {
        this.commonsService = commonsService;
        this.clanmemberService = clanmemberService;
        this.siegeService = siegeService;
    }

    @GetMapping
    @PreAuthorize("hasRole('MEMBER')")
    public String siegeDashboard(Model model, Authentication authentication, HttpSession session) {
        commonsService.fillModel(model, authentication, session);
        ClanmemberEntity activeMember = clanmemberService.getActiveClanmember(session, authentication);

        // 1. Determine "Primary" Clan based on user context
        // Default to the member's hard-assigned group
        ClanGroup primaryGroup = activeMember.getClanGroup();
        if (primaryGroup == null) {
            primaryGroup = ClanGroup.T1; // Fallback if data is missing
        }

        // 2. Check for Session Override (The "Switch" functionality)
        // If the user has switched clans via the nav bar, honor that preference
        Object switchedGroupObj = session.getAttribute("switchedClanGroup");
        if (switchedGroupObj != null) {
            try {
                primaryGroup = ClanGroup.valueOf(switchedGroupObj.toString());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid ClanGroup in session: {}", switchedGroupObj);
            }
        }

        ClanGroup secondaryGroup = (primaryGroup == ClanGroup.T1) ? ClanGroup.T2 : ClanGroup.T1;

        // 3. Fetch or Lazy-Create Sieges
        SiegeEntity primarySiege = getOrCreateSiege(primaryGroup);
        SiegeEntity secondarySiege = getOrCreateSiege(secondaryGroup);

        // 4. Prepare Ordered List for View
        List<SiegeEntity> siegeList = new ArrayList<>();
        siegeList.add(primarySiege);
        siegeList.add(secondarySiege);

        model.addAttribute("siegeList", siegeList);
        model.addAttribute("primaryGroup", primaryGroup); // To highlight the user's active context

        return "siege-dashboard";
    }

    private SiegeEntity getOrCreateSiege(ClanGroup group) {
        return siegeService.getActiveSiege(group)
                .orElseGet(() -> {
                    log.info("Lazy-initializing first siege for {}", group);
                    return siegeService.createNextSiege(group);
                });
    }
}