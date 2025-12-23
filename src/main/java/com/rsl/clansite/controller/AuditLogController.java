package com.rsl.clansite.controller;

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
    public String viewAuditLog(Model model, Authentication authentication) {
        commonsService.fillModel(model, authentication);

        model.addAttribute("logs", auditLogService.getAllLogs());

        return "audit-log";
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasRole('OWNER')")
    public String deleteLogEntry(@PathVariable String id) {
        auditLogService.deleteLogEntry(id);
        return "redirect:/audit-log";
    }
}
