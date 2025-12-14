package com.rsl.clansite.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class DiscordRoleService {
    @Value("${discord.bot-token}")
    private String botToken;

    @Value("${discord.clan-server-id}")
    private String clanServerId;

    private final WebClient webClient = WebClient.create();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private Map<String, String> roleIdToNameMap = Collections.emptyMap();

    @PostConstruct
    public void init() {
        final String DISCORD_ROLES_API = "https://discord.com/api/v10/guilds/" + clanServerId + "/roles";

        log.info("Fetching and caching Discord role names for guild: {}", clanServerId);
        try {
            String rolesJson = webClient.get()
                    .uri(DISCORD_ROLES_API)
                    .header("Authorization", "Bot " + botToken)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode rolesNode = objectMapper.readTree(rolesJson);
            Map<String, String> tempMap = new HashMap<>();

            if (rolesNode.isArray()) {
                for (JsonNode role : rolesNode) {
                    String id = role.get("id").asText();
                    String name = role.get("name").asText();
                    tempMap.put(id, name);
                }
            }
            this.roleIdToNameMap = Collections.unmodifiableMap(tempMap);
            log.info("Successfully cached {} Discord roles.", this.roleIdToNameMap.size());

        } catch (Exception e) {
            log.error("Failed to fetch and cache Discord roles. Role display will show IDs.", e);
        }
    }

    public String getRoleName(String roleId) {
        return roleIdToNameMap.getOrDefault(roleId, roleId);
    }
}
