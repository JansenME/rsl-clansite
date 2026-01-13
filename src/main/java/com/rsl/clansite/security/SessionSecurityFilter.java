package com.rsl.clansite.security;

import com.rsl.clansite.service.ClanmemberService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
public class SessionSecurityFilter extends OncePerRequestFilter {
    private final ClanmemberService clanmemberService;

    public SessionSecurityFilter(ClanmemberService clanmemberService) {
        this.clanmemberService = clanmemberService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && auth.isAuthenticated() && auth instanceof OAuth2AuthenticationToken) {
            OAuth2User oauthUser = ((OAuth2AuthenticationToken) auth).getPrincipal();
            String discordId = oauthUser.getAttribute("id");

            Optional<Set<SimpleGrantedAuthority>> freshAuthoritiesOpt = clanmemberService.getFreshAuthorities(discordId);

            if (freshAuthoritiesOpt.isEmpty()) {
                log.warn("Session Security: User {} no longer exists in DB. Invalidating session.", discordId);
                SecurityContextHolder.clearContext();
                request.getSession().invalidate();
                response.sendRedirect("/");
                return;
            }

            Set<SimpleGrantedAuthority> freshAuthorities = freshAuthoritiesOpt.get();

            Set<String> currentAuths = auth.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.toSet());

            Set<String> freshAuthsStrings = freshAuthorities.stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.toSet());

            if (!currentAuths.equals(freshAuthsStrings)) {
                log.info("Session Security: Permissions changed for {}. Updating session.", discordId);

                Authentication newAuth = new OAuth2AuthenticationToken(
                        oauthUser,
                        freshAuthorities,
                        ((OAuth2AuthenticationToken) auth).getAuthorizedClientRegistrationId()
                );

                SecurityContextHolder.getContext().setAuthentication(newAuth);
            }
        }

        filterChain.doFilter(request, response);
    }
}
