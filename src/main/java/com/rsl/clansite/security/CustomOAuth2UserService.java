package com.rsl.clansite.security;

import com.rsl.clansite.service.ClanmemberService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {
    private static final String OWNER_DISCORD_ID = "270588526267990017";

    private final ClanmemberService clanmemberService;

    @Autowired
    public CustomOAuth2UserService(ClanmemberService clanmemberService) {
        this.clanmemberService = clanmemberService;
    }

    @Value("${discord.bot-token}")
    private String botToken;

    @Value("${discord.clan-server-id}")
    private String clanServerId;

    private static final Set<String> ADMIN_ROLE_IDS = Set.of(
            "1298810713309057067", //Clan Leader
            "1298810856804454461" //Deputy
    );

    private static final Set<String> COORDINATOR_ROLE_IDS = Set.of(
            "1428676592791453778" //Siege Coordinators
    );

    private static final Set<String> MEMBER_ROLE_IDS = Set.of(
            "1298811143699169350", //T1
            "1374237716149174453" //T2
    );

    private static final String DISCORD_MEMBER_API_BASE = "https://discord.com/api/v10/guilds/";

    private final WebClient webClient = WebClient.create();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oauth2User = super.loadUser(userRequest);

        String userId = oauth2User.getAttribute("id");
        String globalName = oauth2User.getAttribute("global_name");
        String avatarHash = oauth2User.getAttribute("avatar");

        JsonNode memberData = getClanmemberData(userId);

        if (memberData == null || memberData.has("message")) {
            log.warn("Access Denied for user {}: Not a member of the clan server or API error (Guild ID: {})", userId, clanServerId);
            throw new OAuth2AuthenticationException("Access Denied: You must be a member of the clan's Discord server to access this application.");
        }

        Set<String> userDiscordRoles = getMemberRoles(memberData);

        try {
            clanmemberService.linkClanmember(userId, globalName, avatarHash, List.copyOf(userDiscordRoles));

        } catch (RuntimeException e) {
            log.warn("Unlinked account login attempt by Discord ID {}: {}", userId, e.getMessage());

            OAuth2Error error = new OAuth2Error(
                    "unlinked_account",
                    e.getMessage(),
                    null
            );

            throw new OAuth2AuthenticationException(error, e);
        }

        Set<SimpleGrantedAuthority> authorities = mapRolesToAuthorities(userDiscordRoles);

        if(OWNER_DISCORD_ID.equals(userId)) {
            log.info("Granting ROLE_OWNER to Discord ID: {}", userId);
            authorities.add(new SimpleGrantedAuthority("ROLE_OWNER"));

            authorities.clear(); authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
            //authorities.clear(); authorities.add(new SimpleGrantedAuthority("ROLE_COORDINATOR"));
            //authorities.clear(); authorities.add(new SimpleGrantedAuthority("ROLE_MEMBER"));
        }

        Map<String, Object> updatedAttributes = new HashMap<>(oauth2User.getAttributes());
        updatedAttributes.put("rawDiscordRoleIds", userDiscordRoles);

        log.info("Logged in user {} has the following roles: {}", globalName, authorities);

        return new DefaultOAuth2User(
                authorities,
                updatedAttributes,
                "id"
        );
    }

    private JsonNode getClanmemberData(String userId) {
        String apiUri = DISCORD_MEMBER_API_BASE + clanServerId + "/members/" + userId;

        try {
            String memberJson = webClient.get()
                    .uri(apiUri)
                    .header("Authorization", "Bot " + botToken)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return objectMapper.readTree(memberJson);

        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                log.warn("User {} not found in guild {}. Cannot retrieve member data.", userId, clanServerId);
                return null;
            }
            log.error("WebClient error fetching Discord member data (HTTP {}): {}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            return null;
        } catch (Exception e) {
            log.error("General error fetching Discord member data: {}", e.getMessage());
            return null;
        }
    }

    private Set<String> getMemberRoles(JsonNode memberData) {
        JsonNode rolesNode = memberData.get("roles");

        if (rolesNode != null && rolesNode.isArray()) {
            Set<String> userRoles = new HashSet<>();
            for (JsonNode roleId : rolesNode) {
                userRoles.add(roleId.asText());
            }
            return userRoles;
        }
        return Set.of();
    }

    private Set<SimpleGrantedAuthority> mapRolesToAuthorities(Set<String> userDiscordRoles) {
        Set<SimpleGrantedAuthority> authorities = new HashSet<>();

        if (userDiscordRoles.stream().anyMatch(ADMIN_ROLE_IDS::contains)) {
            authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        }

        if (userDiscordRoles.stream().anyMatch(COORDINATOR_ROLE_IDS::contains)) {
            authorities.add(new SimpleGrantedAuthority("ROLE_COORDINATOR"));
        }

        if (!authorities.isEmpty() || !userDiscordRoles.isEmpty()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_MEMBER"));
        }

        return authorities;
    }
}
