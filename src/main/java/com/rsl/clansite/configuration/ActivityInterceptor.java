package com.rsl.clansite.configuration;

import com.rsl.clansite.service.ClanmemberService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@Component
public class ActivityInterceptor implements HandlerInterceptor {
    private final ClanmemberService clanmemberService;

    public ActivityInterceptor(ClanmemberService clanmemberService) {
        this.clanmemberService = clanmemberService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {

            String discordId = authentication.getName();
            String path = request.getRequestURI();
            String location = resolveLocation(path);

            clanmemberService.updateLastSeen(discordId, location);
        }

        return true;
    }

    private String resolveLocation(String path) {
        // Exact matches
        if (path.equals("/") || path.equals("/index")) return "Homepage";
        if (path.equals("/profile")) return "Checking Profile";
        if (path.equals("/scraper-dashboard")) return "Monitoring Scraper";

        // Prefix matches (Order matters: specific before general)

        // Clanmembers Controller
        if (path.startsWith("/clanmembers/admin/login-history")) return "Audit: Login History";
        if (path.startsWith("/clanmembers/admin/data-health")) return "Audit: Data Health";
        if (path.startsWith("/clanmembers/add")) return "Roster: Adding Member";
        if (path.startsWith("/clanmembers/edit")) return "Roster: Editing Member";
        if (path.startsWith("/clanmembers")) return "Viewing Roster";

        // Siege Controller
        if (path.startsWith("/siege/overview")) return "Live Siege Battlefield";
        if (path.startsWith("/siege/defense")) return "Managing Siege Defense";
        if (path.startsWith("/siege/history")) return "Viewing Siege Archives";
        if (path.startsWith("/siege")) return "Siege Hub";

        // Champions Controller
        if (path.startsWith("/champions")) return "Browsing Champions";

        // Resources (Ignore static resources to prevent log spam if they bypass filters)
        if (path.startsWith("/styles") || path.startsWith("/images") || path.startsWith("/favicon")) {
            return "Resources";
        }

        // Fallback for unmapped pages
        log.warn("Unmapped user path detected: {}", path);
        return "Browsing Site";
    }
}