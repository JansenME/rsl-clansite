package com.rsl.clansite.controller;

import com.rsl.clansite.model.entity.SiegeConditionEntity;
import com.rsl.clansite.service.CommonsService;
import com.rsl.clansite.service.SiegeConditionService;
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
@RequestMapping("/admin/siege-conditions")
public class SiegeConditionController {

    private final SiegeConditionService siegeConditionService;
    private final CommonsService commonsService;

    public SiegeConditionController(SiegeConditionService siegeConditionService, CommonsService commonsService) {
        this.siegeConditionService = siegeConditionService;
        this.commonsService = commonsService;
    }

    @GetMapping
    @PreAuthorize("hasRole('COORDINATOR')")
    public String viewConditions(Model model, Authentication authentication) {
        commonsService.fillModel(model, authentication);

        List<SiegeConditionEntity> conditions = siegeConditionService.findAllConditions();
        model.addAttribute("conditions", conditions);

        return "siege-conditions";
    }

    @PostMapping("/sync")
    @PreAuthorize("hasRole('COORDINATOR')")
    public String syncConditions(RedirectAttributes redirectAttributes) {
        siegeConditionService.syncConditions();
        redirectAttributes.addFlashAttribute("successMessage", "Database synchronized with system Enums.");
        return "redirect:/admin/siege-conditions";
    }

    @PostMapping("/{id}/toggle")
    @PreAuthorize("hasRole('COORDINATOR')")
    public String toggleCondition(@PathVariable String id,
                                  RedirectAttributes redirectAttributes,
                                  Authentication authentication) { // Added Authentication
        try {
            // Pass Authentication to Service
            siegeConditionService.toggleConditionStatus(id, authentication);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error updating condition: " + e.getMessage());
        }
        return "redirect:/admin/siege-conditions";
    }
}