package com.rsl.clansite.security;

import com.rsl.clansite.model.entity.AppToken;
import com.rsl.clansite.service.AppTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class AppTokenAuthenticationFilter extends OncePerRequestFilter {

    private final AppTokenService appTokenService;
    private static final String TOKEN_HEADER = "X-Sync-Token";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String tokenHeader = request.getHeader(TOKEN_HEADER);

        // No token header? Skip this filter and let other auth mechanisms handle it
        if (tokenHeader == null || tokenHeader.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        log.debug("Found {} header, validating token", TOKEN_HEADER);

        Optional<AppToken> tokenOpt = appTokenService.validateToken(tokenHeader);

        if (tokenOpt.isPresent()) {
            AppToken appToken = tokenOpt.get();

            // Convert stored roles to GrantedAuthority objects
            List<SimpleGrantedAuthority> authorities = appToken.getRoles().stream()
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toList());

            // Create authentication object
            // Principal = Discord ID (so controller can call auth.getName())
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            appToken.getDiscordId(),
                            null, // credentials (not needed)
                            authorities
                    );

            SecurityContextHolder.getContext().setAuthentication(authentication);
            log.debug("Authenticated Discord ID {} via app token", appToken.getDiscordId());
        } else {
            log.warn("Invalid or expired token: {}...", tokenHeader.substring(0, Math.min(8, tokenHeader.length())));
            // Token provided but invalid - clear any existing auth to prevent access
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}