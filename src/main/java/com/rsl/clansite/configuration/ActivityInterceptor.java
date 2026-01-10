package com.rsl.clansite.configuration;

import com.rsl.clansite.service.ClanmemberService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class ActivityInterceptor implements HandlerInterceptor {
    private final ClanmemberService clanmemberService;

    public ActivityInterceptor(ClanmemberService clanmemberService) {
        this.clanmemberService = clanmemberService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // Only track authenticated users who are NOT "Anonymous" (Guests)
        if (authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {

            String discordId = authentication.getName();

            // Fire and forget (The service handles the 1-minute throttling)
            clanmemberService.updateLastSeen(discordId);
        }

        return true; // Continue processing the request
    }
}
