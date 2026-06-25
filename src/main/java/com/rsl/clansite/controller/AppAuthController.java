package com.rsl.clansite.controller;

import com.rsl.clansite.model.entity.UserRefreshToken;
import com.rsl.clansite.repository.UserRefreshTokenRepository;
import com.rsl.clansite.security.JwtService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/app")
public class AppAuthController {

    private final JwtService jwtService;
    private final UserRefreshTokenRepository refreshTokenRepository;

    public AppAuthController(JwtService jwtService, UserRefreshTokenRepository refreshTokenRepository) {
        this.jwtService = jwtService;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @GetMapping("/login")
    public ResponseEntity<Void> initiateAppLogin(HttpServletResponse response) {
        log.info("[KLOEPIEBOT-AUTH] /api/app/login endpoint hit. Setting APP_LOGIN_FLAG cookie...");

        ResponseCookie cookie = ResponseCookie.from("APP_LOGIN_FLAG", "true")
                .path("/")
                .maxAge(300)
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        log.info("[KLOEPIEBOT-AUTH] Redirecting browser to Discord OAuth2...");
        return ResponseEntity.status(HttpStatus.FOUND)
                .header("Location", "/oauth2/authorization/discord")
                .build();
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refreshAccessToken(@RequestBody Map<String, String> request) {
        // ... (Keep your existing refresh logic exactly the same)
        String refreshTokenString = request.get("refreshToken");

        if (refreshTokenString == null || refreshTokenString.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Refresh token is required"));
        }

        return refreshTokenRepository.findByToken(refreshTokenString)
                .map(storedToken -> {
                    if (storedToken.getExpiryDate().isBefore(Instant.now())) {
                        refreshTokenRepository.delete(storedToken);
                        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                .body(Map.of("error", "Refresh token expired"));
                    }

                    String discordId = storedToken.getDiscordId();
                    List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));

                    String newAccessToken = jwtService.generateAccessToken(discordId, authorities);
                    String newRefreshTokenString = jwtService.generateRefreshTokenString();

                    refreshTokenRepository.delete(storedToken);

                    UserRefreshToken newRefreshToken = UserRefreshToken.builder()
                            .discordId(discordId)
                            .token(newRefreshTokenString)
                            .expiryDate(Instant.now().plus(30, ChronoUnit.DAYS))
                            .build();

                    refreshTokenRepository.save(newRefreshToken);

                    return ResponseEntity.ok(Map.of(
                            "accessToken", newAccessToken,
                            "refreshToken", newRefreshTokenString
                    ));
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Invalid refresh token")));
    }
}