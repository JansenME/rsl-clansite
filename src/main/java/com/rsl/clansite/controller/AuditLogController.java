package com.rsl.clansite.controller;

import com.rsl.clansite.model.entity.AuditLogEntity;
import com.rsl.clansite.model.enums.AuditAction;
import com.rsl.clansite.service.AuditLogService;
import com.rsl.clansite.service.CommonsService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/audit-log")
public class AuditLogController {
    private final AuditLogService auditLogService;
    private final CommonsService commonsService;

    public AuditLogController(AuditLogService auditLogService, CommonsService commonsService) {
        this.auditLogService = auditLogService;
        this.commonsService = commonsService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public String viewAuditLog(
            Model model,
            Authentication authentication,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) String target) {

        commonsService.fillModel(model, authentication);

        List<AuditLogEntity> logs = auditLogService.searchLogs(startDate, endDate, actor, action, target);

        if (logs.size() > 100) {
            logs = logs.subList(0, 100);

            model.addAttribute("limitWarning", "Showing the first 100 results only. There are more records matching your criteria. Please use the filters to narrow down your search.");
        }

        model.addAttribute("logs", logs);

        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);
        model.addAttribute("actor", actor);
        model.addAttribute("selectedAction", action);
        model.addAttribute("target", target);

        model.addAttribute("auditActions", AuditAction.values());

        return "audit-log";
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasRole('OWNER')")
    public String deleteLogEntry(@PathVariable String id) {
        auditLogService.deleteLogEntry(id);
        return "redirect:/audit-log";
    }
}
