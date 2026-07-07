package com.rsl.clansite.controller;

import com.rsl.clansite.model.entity.RaidUser;
import com.rsl.clansite.repository.RaidUserRepository;
import com.rsl.clansite.security.JwtService;
import com.rsl.clansite.service.AppTokenService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/app")
public class AppApiController {
    private static final String TOKEN_HEADER = "X-Sync-Token";

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
}
