package com.rsl.clansite.configuration;

import com.rsl.clansite.service.ClanmemberService;
import jakarta.annotation.Nonnull;
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
    public boolean preHandle(@Nonnull HttpServletRequest request, @Nonnull HttpServletResponse response, @Nonnull Object handler) throws Exception {
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
        // Main pages
        if (path.equals("/") || path.isEmpty() || path.equals("/index")) return "Homepage";
        if (path.startsWith("/error")) return "Error page";
        if (path.startsWith("/login")) return "Login page";

        // Prefix matches (Order matters: specific before general)

        // Audit Log Controller
        if (path.startsWith("/audit-log")) return "Audit Log";

        // Champions Controller
        if (path.startsWith("/champions/new")) return "Creating Champion";
        if (path.matches("^/champions/[a-fA-F0-9]{24}/edit$")) return "Editing Champion";
        if (path.matches("^/champions/[a-fA-F0-9]{24}$")) return "Checking Champion Details";
        if (path.startsWith("/champions")) return "Browsing Champions";

        // Clanmembers Controller
        if (path.startsWith("/clanmembers/admin/login-history")) return "Login History";
        if (path.startsWith("/clanmembers/admin/data-health")) return "Discord Data Health";
        if (path.startsWith("/clanmembers/add")) return "Adding Member";
        if (path.startsWith("/clanmembers/edit")) return "Editing Member";
        if (path.startsWith("/clanmembers")) return "Viewing Roster";

        // Profile Controller
        if (path.startsWith("/profile")) return "Viewing Profile";

        // Scraper Controller
        if (path.startsWith("/admin/scraper")) return "Viewing Champion Scraper";
        if (path.startsWith("/admin/data-health")) return "Viewing Champion Data Health";

        // SiegeCondition Controller
        if (path.startsWith("/admin/siege-conditions")) return "Viewing Siege Conditions";

        // Siege Controller
        if (path.startsWith("/siege/overview")) return "Live Siege Battlefield";
        if (path.startsWith("/siege/defense")) return "Managing Siege Defense";
        if (path.startsWith("/siege/history")) return "Viewing Siege Archives";
        if (path.startsWith("/siege")) return "Siege Hub";

        // Teams Controller
        if (path.startsWith("/teams/builder")) return "Build a team";

        // Resources (Ignore static resources to prevent log spam if they bypass filters)
        if (path.startsWith("/styles") || path.startsWith("/images") || path.startsWith("/favicon")) {
            return "Resources";
        }

        // Fallback for unmapped pages
        log.warn("Unmapped user path detected: {}", path);
        return "Browsing Site";
    }
}