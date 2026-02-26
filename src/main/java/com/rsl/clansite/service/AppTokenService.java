package com.rsl.clansite.service;

import com.rsl.clansite.model.entity.AppToken;
import com.rsl.clansite.repository.AppTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AppTokenService {

    private final AppTokenRepository appTokenRepository;
    private final MongoTemplate mongoTemplate;

    public String generateToken(Authentication authentication, String sessionId) {
        if (!(authentication.getPrincipal() instanceof OAuth2User oauth2User)) {
            throw new IllegalStateException("User not authenticated via OAuth2");
        }

        String discordId = oauth2User.getName();

        // Extract roles from authentication
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

        // Invalidate old token for this user (one token per user)
        appTokenRepository.deleteByDiscordId(discordId);
        log.info("Invalidated old token for Discord ID: {}", discordId);

        // Generate new token
        String tokenValue = UUID.randomUUID().toString();

        AppToken appToken = AppToken.builder()
                .token(tokenValue)
                .discordId(discordId)
                .roles(roles)
                .sessionId(sessionId)
                .createdAt(Instant.now())
                .build();

        appTokenRepository.save(appToken);
        log.info("Generated new app token for Discord ID: {}", discordId);

        return tokenValue;
    }

    public Optional<AppToken> validateToken(String tokenValue) {
        Optional<AppToken> tokenOpt = appTokenRepository.findByToken(tokenValue);

        if (tokenOpt.isEmpty()) {
            log.warn("Token not found: {}", tokenValue.substring(0, 8) + "...");
            return Optional.empty();
        }

        AppToken appToken = tokenOpt.get();

        // Check if web session is still active
        if (!isSessionActive(appToken.getSessionId())) {
            log.warn("Token found but session {} is no longer active", appToken.getSessionId());
            return Optional.empty();
        }

        return Optional.of(appToken);
    }

    private boolean isSessionActive(String sessionId) {
        try {
            // Spring Session MongoDB stores sessions in 'sessions' collection
            Query query = new Query(Criteria.where("_id").is(sessionId));
            return mongoTemplate.exists(query, "sessions");
        } catch (Exception e) {
            log.error("Error checking session status", e);
            return false;
        }
    }
}