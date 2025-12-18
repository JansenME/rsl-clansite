package com.rsl.clansite.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class DiscordRoleService {
    public static final String CLAN_LEADER_ROLE_ID = "1298810713309057067";
    public static final String DEPUTY_ROLE_ID = "1298810856804454461";
    public static final String SIEGE_COORDINATOR_ROLE_ID = "1428676592791453778";
    public static final String T1_ROLE_ID = "1298811143699169350";
    public static final String T2_ROLE_ID = "1374237716149174453";

    @Value("${discord.bot-token}")
    private String botToken;

    @Value("${discord.clan-server-id}")
    private String clanServerId;

    private final WebClient webClient = WebClient.create();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private Map<String, String> roleIdToNameMap = Collections.emptyMap();
    @Getter
    private List<String> orderedRoleIds = Collections.emptyList();

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
            List<RoleData> rolesList = new ArrayList<>();

            if (rolesNode.isArray()) {
                for (JsonNode role : rolesNode) {
                    String id = role.get("id").asText();
                    String name = role.get("name").asText();
                    int position = role.get("position").asInt();

                    tempMap.put(id, name);
                    rolesList.add(new RoleData(id, position));
                }
            }

            rolesList.sort(Comparator.comparingInt(RoleData::position).reversed());

            List<String> tempOrderedIds = rolesList.stream()
                    .map(RoleData::id)
                    .toList();

            this.roleIdToNameMap = Collections.unmodifiableMap(tempMap);
            this.orderedRoleIds = Collections.unmodifiableList(tempOrderedIds);
            log.info("Successfully cached {} Discord roles.", this.roleIdToNameMap.size());
        } catch (Exception e) {
            log.error("Failed to fetch and cache Discord roles. Role display will show IDs.", e);
        }
    }

    public String getRoleName(String roleId) {
        return roleIdToNameMap.getOrDefault(roleId, roleId);
    }

    private record RoleData(String id, int position) {}
}
