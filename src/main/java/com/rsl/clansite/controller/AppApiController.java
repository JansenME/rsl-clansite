package com.rsl.clansite.controller;

import com.rsl.clansite.model.entity.RaidUser;
import com.rsl.clansite.repository.RaidUserRepository;
import com.rsl.clansite.security.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/app")
public class AppApiController {
    private static final String TOKEN_HEADER = "X-Sync-Token";

    private static final String APP_SECRET = "KloepieBot_S3cr3t_H4sh_K3y_2026!";

    public static class RaidUserUpsertRequest {
        private Long raidId;
        private String playerName;

        public Long getRaidId() { return raidId; }
        public void setRaidId(Long raidId) { this.raidId = raidId; }
        public String getPlayerName() { return playerName; }
        public void setPlayerName(String playerName) { this.playerName = playerName; }
    }

    private final JwtService jwtService;
    private final RaidUserRepository raidUserRepository;

    public AppApiController(JwtService jwtService, RaidUserRepository raidUserRepository) {
        this.jwtService = jwtService;
        this.raidUserRepository = raidUserRepository;
    }

    @PostMapping("/raididtoplayername")
    public ResponseEntity<Map<Long, String>> getRaidIdToPlayerNameMapping (@RequestBody List<Long> raidIds, HttpServletRequest request) {
        if(!hasValidToken(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Map<Long, String> result = raidUserRepository.findByRaidIdIn(raidIds).stream()
                .collect(Collectors.toMap(
                        RaidUser::getRaidId,
                        RaidUser::getPlayerName
                ));

        return ResponseEntity.ok(result);
    }

    @PostMapping("/upsertplayer")
    public ResponseEntity<Void> upsertPlayer(@RequestBody RaidUserUpsertRequest requestData, HttpServletRequest request) {
        // 1. Validate JWT
        if(!hasValidToken(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (requestData.getRaidId() == null || requestData.getPlayerName() == null) {
            return ResponseEntity.badRequest().build();
        }

        // 2. Validate HMAC Signature
        String signatureHeader = request.getHeader("X-App-Signature");
        String payloadToHash = requestData.getRaidId() + ":" + requestData.getPlayerName();

        if (!isValidSignature(payloadToHash, signatureHeader)) {
            log.warn("[SECURITY] Invalid HMAC signature for Upsert attempt.");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // 3. Extract Discord ID
        String tokenHeader = request.getHeader(TOKEN_HEADER);
        String cleanToken = tokenHeader.startsWith("Bearer ") ? tokenHeader.substring(7) : tokenHeader;
        String discordId = jwtService.extractDiscordId(cleanToken);

        // 4. Database Logic
        Optional<RaidUser> existingUserOpt = raidUserRepository.findByRaidId(requestData.getRaidId());

        if (existingUserOpt.isPresent()) {
            RaidUser existingUser = existingUserOpt.get();

            if (existingUser.getDiscordId() == null) {
                // ADOPTION: It was seeded manually! Claim it for this Discord user.
                existingUser.setDiscordId(discordId);
                existingUser.setPlayerName(requestData.getPlayerName());
                raidUserRepository.save(existingUser);
                log.info("[API SYNC] Adopted seeded Raid ID {} for Discord user {}", existingUser.getRaidId(), discordId);

            } else if (existingUser.getDiscordId().equals(discordId)) {
                // UPDATE: It belongs to them, update the name if it changed.
                if (!existingUser.getPlayerName().equals(requestData.getPlayerName())) {
                    existingUser.setPlayerName(requestData.getPlayerName());
                    raidUserRepository.save(existingUser);
                    log.info("[API SYNC] Updated name for Raid ID {}: {}", existingUser.getRaidId(), existingUser.getPlayerName());
                }
            } else {
                // REJECT: Belongs to someone else!
                log.warn("[SECURITY] Discord user {} tried to alter Raid ID {} owned by {}", discordId, existingUser.getRaidId(), existingUser.getDiscordId());
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
        } else {
            // INSERT: Check quota first!
            long currentAccounts = raidUserRepository.countByDiscordId(discordId);
            if (currentAccounts >= 10) {
                log.warn("[API SYNC] Discord user {} hit the 10 account limit. Ignoring silently.", discordId);
                return ResponseEntity.ok().build(); // Silent fail
            }

            RaidUser newUser = new RaidUser();
            newUser.setRaidId(requestData.getRaidId());
            newUser.setPlayerName(requestData.getPlayerName());
            newUser.setDiscordId(discordId);
            raidUserRepository.save(newUser);
            log.info("[API SYNC] Inserted new Raid User {} for Discord ID {}", newUser.getPlayerName(), discordId);
        }

        return ResponseEntity.ok().build();
    }

    private boolean hasValidToken(HttpServletRequest request) {
        String tokenHeader = request.getHeader(TOKEN_HEADER);

        if (tokenHeader == null || tokenHeader.isBlank()) {
            log.warn("[API SYNC] Request blocked: X-Sync-Token header is missing.");
            return false;
        }

        if (tokenHeader.startsWith("Bearer ")) {
            tokenHeader = tokenHeader.substring(7);
        }

        boolean isValid = jwtService.isTokenValid(tokenHeader);
        if (!isValid) {
            log.warn("[API SYNC] Request blocked: Provided JWT is expired or invalid.");
        }

        return isValid;
    }

    private boolean isValidSignature(String payload, String signatureHeader) {
        if (signatureHeader == null || signatureHeader.isBlank()) return false;

        try {
            Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secret_key = new SecretKeySpec(APP_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            sha256_HMAC.init(secret_key);

            byte[] hash = sha256_HMAC.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String expectedSignature = Base64.getEncoder().encodeToString(hash);

            return expectedSignature.equals(signatureHeader);
        } catch (Exception e) {
            log.error("[SECURITY] HMAC hashing failed", e);
            return false;
        }
    }
}
