package com.rsl.clansite.controller;

import com.rsl.clansite.model.entity.UserRefreshToken;
import com.rsl.clansite.repository.UserRefreshTokenRepository;
import com.rsl.clansite.security.JwtService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

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
    public ResponseEntity<Void> initiateAppLogin(HttpSession session) {
        session.setAttribute("APP_LOGIN_FLAG", true);

        return ResponseEntity.status(HttpStatus.FOUND)
                .header("Location", "/oauth2/authorization/discord")
                .build();
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refreshAccessToken(@RequestBody Map<String, String> request) {
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

                    // Defaulting to base user authority for the app session sync.
                    // If you require dynamic roles during refresh, you can wire in your
                    // ClanmemberRepository or SecurityService here to fetch updated roles.
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
