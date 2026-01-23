package com.rsl.clansite.service;

import com.rsl.clansite.model.ClanmemberViewData;
import com.rsl.clansite.model.entity.ClanmemberEntity;
import com.rsl.clansite.model.enums.QuickLink;
import com.rsl.clansite.security.SecurityService;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Year;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Slf4j
@Service
public class CommonsService {
    private static final DateTimeFormatter APP_DATE_FORMATTER = DateTimeFormatter.ofPattern("d-M-yyyy");

    private final ClanmemberService clanmemberService;
    private final BuildProperties buildProperties;
    private final SecurityService securityService;

    public CommonsService(ClanmemberService clanmemberService,
                          @Autowired(required = false) BuildProperties buildProperties,
                          SecurityService securityService) {
        this.clanmemberService = clanmemberService;
        this.buildProperties = buildProperties;
        this.securityService = securityService;
    }

    public String generateImageFilename(String championName) {
        if (championName == null || championName.trim().isEmpty()) {
            return "placeholder.png";
        }

        String cleanName = championName.toLowerCase().replaceAll("[^a-z0-9\\s]", "");

        return cleanName.replaceAll("\\s+", " ").trim().replace(" ", "-") + ".png";
    }

    public void fillModel(Model model, Authentication authentication) {
        fillModel(model, authentication, null);
    }

    public void fillModel(Model model, Authentication authentication, HttpSession session) {
        model.addAttribute("versionNumber", getAppVersion());
        model.addAttribute("currentYear", String.valueOf(Year.now().getValue()));
        model.addAttribute("applicationDate", getAppBuildDate());

        String supportId = resolveSessionId(session);
        model.addAttribute("supportId", supportId);

        if (authentication != null && authentication.isAuthenticated()) {
            ClanmemberViewData viewData = clanmemberService.getUserViewData(authentication);
            model.addAttribute("clanmemberViewData", viewData);
            model.addAttribute("navUserViewData", viewData);

            if (session != null) {
                ClanmemberEntity activeMember = clanmemberService.getActiveClanmember(session, authentication);
                model.addAttribute("activeMember", activeMember);
            }

            model.addAttribute("isLoggedIn", true);
            model.addAttribute("quickLinks", getVisibleQuickLinks(authentication, session));
        } else {
            model.addAttribute("isLoggedIn", false);
        }
    }

    public List<VisibleQuickLink> getVisibleQuickLinks(Authentication authentication, HttpSession session) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return List.of();
        }

        Collection<? extends GrantedAuthority> reachableAuthorities =
                securityService.getReachableAuthorities(authentication);

        String activeMemberId = "";
        if (session != null) {
            ClanmemberEntity active = clanmemberService.getActiveClanmember(session, authentication);
            if (active != null) {
                activeMemberId = active.getId().toHexString();
            }
        }

        List<VisibleQuickLink> visibleLinks = new ArrayList<>();

        for (QuickLink link : QuickLink.values()) {
            if (reachableAuthorities.contains(new SimpleGrantedAuthority(link.getRequiredRole()))) {

                String finalUrl = link.getUrl();

                if (link == QuickLink.EDIT_ROSTER) {
                    if (!activeMemberId.isEmpty()) {
                        finalUrl = link.getUrl() + activeMemberId;
                    } else {
                        continue;
                    }
                }

                visibleLinks.add(new VisibleQuickLink(link.getLabel(), finalUrl));
            }
        }
        return visibleLinks;
    }

    public record VisibleQuickLink(String label, String url) {}

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

    private String resolveSessionId(HttpSession explicitSession) {
        if (explicitSession != null) {
            return explicitSession.getId();
        }
        try {
            ServletRequestAttributes attr = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attr != null && attr.getRequest().getSession(false) != null) {
                return attr.getRequest().getSession(false).getId();
            }
        } catch (Exception ignored) {
            // Context not available
        }
        return "N/A";
    }
}