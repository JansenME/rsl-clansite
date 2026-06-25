package com.rsl.clansite.security;

import com.rsl.clansite.model.entity.UserRefreshToken;
import com.rsl.clansite.repository.UserRefreshTokenRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Component
public class AppAuthenticationSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    private final JwtService jwtService;
    private final UserRefreshTokenRepository refreshTokenRepository;

    public AppAuthenticationSuccessHandler(JwtService jwtService, UserRefreshTokenRepository refreshTokenRepository) {
        this.jwtService = jwtService;
        this.refreshTokenRepository = refreshTokenRepository;
        setDefaultTargetUrl("/");
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws ServletException, IOException {

        boolean isAppLogin = false;
        Cookie[] cookies = request.getCookies();

        if (cookies != null) {
            for (Cookie cookie : cookies) {
                log.info("[KLOEPIEBOT-AUTH] Found Cookie -> Name: '{}', Value: '{}'", cookie.getName(), cookie.getValue());
                if ("APP_LOGIN_FLAG".equals(cookie.getName()) && "true".equals(cookie.getValue())) {
                    isAppLogin = true;

                    ResponseCookie clearCookie = ResponseCookie.from("APP_LOGIN_FLAG", "")
                            .path("/")
                            .maxAge(0)
                            .secure(true)
                            .sameSite("Lax")
                            .build();
                    response.addHeader(HttpHeaders.SET_COOKIE, clearCookie.toString());
                    break;
                }
            }
        } else {
            log.info("[KLOEPIEBOT-AUTH] The browser sent ZERO cookies back to the server.");
        }

        if (isAppLogin) {
            OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
            String discordId = oAuth2User.getAttribute("id");
            if (discordId == null) discordId = oAuth2User.getName();

            String username = oAuth2User.getAttribute("global_name");
            if (username == null) username = oAuth2User.getAttribute("username");
            if (username == null) username = "Unknown User";

            String encodedUsername = URLEncoder.encode(username, StandardCharsets.UTF_8);

            String accessToken = jwtService.generateAccessToken(discordId, authentication.getAuthorities());
            String refreshTokenString = jwtService.generateRefreshTokenString();

            UserRefreshToken refreshToken = UserRefreshToken.builder()
                    .discordId(discordId)
                    .token(refreshTokenString)
                    .expiryDate(Instant.now().plus(30, ChronoUnit.DAYS))
                    .build();

            refreshTokenRepository.deleteByDiscordId(discordId);
            refreshTokenRepository.save(refreshToken);

            String targetUrl = "http://127.0.0.1:45321/auth-success?token=" + accessToken + "&refreshToken=" + refreshTokenString + "&username=" + encodedUsername;
            getRedirectStrategy().sendRedirect(request, response, targetUrl);
        }
    }
}