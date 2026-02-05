package com.rsl.clansite.security;

import com.rsl.clansite.repository.AppTokenRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class AppTokenAuthenticationFilter extends OncePerRequestFilter {

    private final AppTokenRepository appTokenRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        // Check for "Bearer <TOKEN>"
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);

            appTokenRepository.findByToken(token).ifPresent(appToken -> {

                // Convert stored String roles to GrantedAuthority objects
                List<SimpleGrantedAuthority> authorities = appToken.getRoles().stream()
                        .map(SimpleGrantedAuthority::new)
                        .collect(Collectors.toList());

                // Create the Authentication object
                // Principal = DiscordId (So controller can call auth.getName())
                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                        appToken.getDiscordId(),
                        token,
                        authorities
                );

                SecurityContextHolder.getContext().setAuthentication(auth);
            });
        }

        filterChain.doFilter(request, response);
    }
}
