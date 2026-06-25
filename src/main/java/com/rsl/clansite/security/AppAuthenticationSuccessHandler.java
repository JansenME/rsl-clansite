package com.rsl.clansite.security;

import com.rsl.clansite.model.entity.UserRefreshToken;
import com.rsl.clansite.repository.UserRefreshTokenRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
public class AppAuthenticationSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {
    private final JwtService jwtService;
    private final UserRefreshTokenRepository refreshTokenRepository;

    public AppAuthenticationSuccessHandler(JwtService jwtService, UserRefreshTokenRepository refreshTokenRepository) {
        this.jwtService = jwtService;
        this.refreshTokenRepository = refreshTokenRepository;
        // This ensures normal web users go to the index/profile if there isn't a saved request
        setDefaultTargetUrl("/");
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws ServletException, IOException {

        Boolean isAppLogin = (Boolean) request.getSession().getAttribute("APP_LOGIN_FLAG");

        if (Boolean.TRUE.equals(isAppLogin)) {
            // Clean up the session flag so it doesn't pollute future web logins
            request.getSession().removeAttribute("APP_LOGIN_FLAG");

            OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
            // Discord provides the user's unique ID under the "id" attribute
            String discordId = oAuth2User.getAttribute("id");

            if (discordId == null) {
                discordId = oAuth2User.getName();
            }

            String accessToken = jwtService.generateAccessToken(discordId, authentication.getAuthorities());
            String refreshTokenString = jwtService.generateRefreshTokenString();

            UserRefreshToken refreshToken = UserRefreshToken.builder()
                    .discordId(discordId)
                    .token(refreshTokenString)
                    .expiryDate(Instant.now().plus(30, ChronoUnit.DAYS))
                    .build();

            // Invalidate any previous desktop sessions for this user to enforce single active session
            refreshTokenRepository.deleteByDiscordId(discordId);
            refreshTokenRepository.save(refreshToken);

            // Redirect back to the local C# web server listener
            String targetUrl = "http://127.0.0.1:45321/auth-success?token=" + accessToken + "&refreshToken=" + refreshTokenString;
            getRedirectStrategy().sendRedirect(request, response, targetUrl);
        } else {
            // Standard website login flow
            super.onAuthenticationSuccess(request, response, authentication);
        }
    }
}
