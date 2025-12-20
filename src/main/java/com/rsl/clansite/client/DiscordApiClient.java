package com.rsl.clansite.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rsl.clansite.model.dto.DiscordRoleDTO;
import com.rsl.clansite.model.dto.NewClanmemberDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class DiscordApiClient {
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public DiscordApiClient(@Value("${discord.bot-token}") String botToken,
                            @Value("${discord.clan-server-id}") String clanServerId) {

        this.webClient = WebClient.builder()
                .baseUrl("https://discord.com/api/v10/guilds/" + clanServerId)
                .defaultHeader("Authorization", "Bot " + botToken)
                .build();

        this.objectMapper = new ObjectMapper();
    }

    public Optional<NewClanmemberDTO> getDiscordMember(String discordId) {
        try {
            String jsonResponse = webClient.get()
                    .uri("/members/" + discordId)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return Optional.of(parseDiscordResponse(discordId, jsonResponse));

        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                return Optional.empty();
            }
            throw new RuntimeException("Discord API Error: " + e.getStatusCode() + " - " + e.getResponseBodyAsString());
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch Discord Member: " + e.getMessage());
        }
    }

    public List<DiscordRoleDTO> getGuildRoles() {
        try {
            String jsonResponse = webClient.get()
                    .uri("/roles")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return parseRolesResponse(jsonResponse);

        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch Discord Roles: " + e.getMessage());
        }
    }

    private NewClanmemberDTO parseDiscordResponse(String discordId, String json) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        JsonNode userNode = root.path("user");
        JsonNode nickNode = root.path("nick");

        String globalName = userNode.path("global_name").asText();
        String username = userNode.path("username").asText();
        String avatarHash = userNode.path("avatar").asText();

        String discordName = globalName.isBlank() ? username : globalName;

        String nickname = (nickNode.isMissingNode() || nickNode.isNull() || nickNode.asText().isBlank())
                ? discordName
                : nickNode.asText();

        List<String> roleIds = new ArrayList<>();
        JsonNode rolesNode = root.path("roles");
        if (rolesNode.isArray()) {
            for (JsonNode role : rolesNode) {
                roleIds.add(role.asText());
            }
        }

        NewClanmemberDTO dto = new NewClanmemberDTO();
        dto.setDiscordId(discordId);
        dto.setDiscordName(discordName);
        dto.setPlayerNickname(nickname);
        dto.setAvatarHash(avatarHash);
        dto.setDiscordRoles(roleIds);

        return dto;
    }

    private List<DiscordRoleDTO> parseRolesResponse(String json) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        List<DiscordRoleDTO> roles = new ArrayList<>();

        if (root.isArray()) {
            for (JsonNode node : root) {
                DiscordRoleDTO role = new DiscordRoleDTO();
                role.setId(node.get("id").asText());
                role.setName(node.get("name").asText());
                role.setPosition(node.get("position").asInt());
                roles.add(role);
            }
        }
        return roles;
    }
}
