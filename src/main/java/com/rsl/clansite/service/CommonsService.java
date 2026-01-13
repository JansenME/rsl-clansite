package com.rsl.clansite.service;

import com.rsl.clansite.model.ClanmemberViewData;
import com.rsl.clansite.model.entity.ClanmemberEntity;
import com.rsl.clansite.model.enums.QuickLink;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;

import java.time.Year;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class CommonsService {
    private static final DateTimeFormatter APP_DATE_FORMATTER = DateTimeFormatter.ofPattern("d-M-yyyy");

    private final ClanmemberService clanmemberService;
    private final BuildProperties buildProperties;

    public CommonsService(ClanmemberService clanmemberService,
                          @Autowired(required = false) BuildProperties buildProperties) {
        this.clanmemberService = clanmemberService;
        this.buildProperties = buildProperties;
    }

    public String generateImageFilename(String championName) {
        if (championName == null || championName.trim().isEmpty()) {
            return "placeholder.png";
        }

        String cleanName = championName.toLowerCase().replaceAll("[^a-z0-9\\s]", ""); // Keep only alphanumeric + whitespace

        return cleanName.replaceAll("\\s+", " ").trim().replace(" ", "-") + ".png";
    }

    public void fillModel(Model model, Authentication authentication) {
        fillModel(model, authentication, null);
    }

    public void fillModel(Model model, Authentication authentication, HttpSession session) {
        model.addAttribute("versionNumber", getAppVersion());
        model.addAttribute("currentYear", String.valueOf(Year.now().getValue()));
        model.addAttribute("applicationDate", getAppBuildDate());

        if (authentication != null && authentication.isAuthenticated()) {
            ClanmemberViewData viewData = clanmemberService.getUserViewData(authentication);
            model.addAttribute("clanmemberViewData", viewData);

            if (session != null) {
                ClanmemberEntity activeMember = clanmemberService.getActiveClanmember(session, authentication);
                model.addAttribute("activeMember", activeMember);
            }

            model.addAttribute("isLoggedIn", true);
            model.addAttribute("quickLinks", getVisibleQuickLinks(authentication));
        } else {
            model.addAttribute("isLoggedIn", false);
        }
    }

    public List<QuickLink> getVisibleQuickLinks(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return List.of();
        }

        List<QuickLink> visibleLinks = new ArrayList<>();

        // Get user roles as a list of strings (e.g., ["ROLE_ADMIN", "ROLE_MEMBER"])
        List<String> userRoles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        // Check if user is OWNER (assuming Owner has access to Admin links via Hierarchy or explicit check)
        boolean isOwner = userRoles.contains("ROLE_OWNER");
        boolean isAdmin = userRoles.contains("ROLE_ADMIN") || isOwner;

        for (QuickLink link : QuickLink.values()) {
            boolean hasAccess = false;

            // Simple Role Check Logic
            if ("ROLE_OWNER".equals(link.getRequiredRole())) {
                hasAccess = isOwner;
            } else if ("ROLE_ADMIN".equals(link.getRequiredRole())) {
                hasAccess = isAdmin;
            } else {
                // If we add public links later
                hasAccess = true;
            }

            if (hasAccess) {
                visibleLinks.add(link);
            }
        }
        return visibleLinks;
    }

    private String getAppVersion() {
        if (buildProperties != null) {
            return buildProperties.getVersion();
        }
        return "dev-local";
    }

    private String getAppBuildDate() {
        if (buildProperties != null && buildProperties.getTime() != null) {
            return buildProperties.getTime()
                    .atZone(ZoneId.of("Europe/Paris"))
                    .format(APP_DATE_FORMATTER);
        }
        return "Unknown Date";
    }
}
