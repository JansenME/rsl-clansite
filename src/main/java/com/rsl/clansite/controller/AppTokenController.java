package com.rsl.clansite.controller;

import com.rsl.clansite.model.entity.AppToken;
import com.rsl.clansite.repository.AppTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.view.RedirectView;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Controller
@RequiredArgsConstructor
public class AppTokenController {

    private final AppTokenRepository appTokenRepository;

    // 1. GENERATE TOKEN (Browser -> Deep Link)
    @GetMapping("/profile/connect-app")
    public RedirectView connectDesktopApp(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof OAuth2User)) {
            return new RedirectView("/login");
        }

        OAuth2User user = (OAuth2User) authentication.getPrincipal();
        String discordId = user.getName();

        String globalName = user.getAttribute("global_name");
        String username = user.getAttribute("username");

        String discordName = !StringUtils.hasText(globalName) ? username : globalName;

        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

        String token = UUID.randomUUID().toString();

        // Save to Database
        AppToken appToken = new AppToken(token, discordId, roles, discordName);
        appTokenRepository.save(appToken);

        log.info("Generated App Token for: {} ({})", discordName, discordId);

        // SECURE CHANGE: We only send the Token and Name (for UI display).
        // Roles are NOT sent. The app must fetch them using the token.
        String encodedName = UriUtils.encode(discordName, StandardCharsets.UTF_8);

        return new RedirectView("clansite://auth?key=" + token + "&name=" + encodedName);
    }

    // 2. VERIFY TOKEN (Desktop App -> JSON Response)
    @GetMapping("/api/app/session")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> verifySession(@RequestHeader("Authorization") String authHeader) {
        // Basic Bearer Validation
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String token = authHeader.substring(7); // Remove "Bearer "

        // Find the token in the DB (Source of Truth)
        Optional<AppToken> appTokenOpt = appTokenRepository.findByToken(token);

        if (appTokenOpt.isEmpty()) {
            log.warn("App attempted login with invalid token");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        AppToken appToken = appTokenOpt.get();

        // Construct the Secure Response
        Map<String, Object> response = new HashMap<>();
        response.put("globalName", appToken.getGlobalName());
        response.put("roles", appToken.getRoles()); // The App must trust THIS list

        return ResponseEntity.ok(response);
    }
}