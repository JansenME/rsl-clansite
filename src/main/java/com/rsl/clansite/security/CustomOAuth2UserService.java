package com.rsl.clansite.security;

import com.rsl.clansite.client.DiscordApiClient;
import com.rsl.clansite.model.dto.NewClanmemberDTO;
import com.rsl.clansite.service.ClanmemberService;
import com.rsl.clansite.service.DiscordRoleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {
    private final ClanmemberService clanmemberService;
    private final DiscordApiClient discordApiClient;
    private final DiscordRoleService discordRoleService;

    public CustomOAuth2UserService(ClanmemberService clanmemberService,
                                   DiscordApiClient discordApiClient,
                                   DiscordRoleService discordRoleService) {
        this.clanmemberService = clanmemberService;
        this.discordApiClient = discordApiClient;
        this.discordRoleService = discordRoleService;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oauth2User = fetchUserInfo(userRequest);
        String userId = oauth2User.getAttribute("id");

        // STEP 1: Fetch the "Clean" DTO from our updated Client
        // This runs the logic: GlobalName > Username (and handles "null" strings)
        NewClanmemberDTO memberDto = discordApiClient.getDiscordMember(userId)
                .orElseThrow(() -> {
                    log.warn("Access Denied for user {}: Not a member of the clan server.", userId);
                    return new OAuth2AuthenticationException(new OAuth2Error(
                            "not_in_guild",
                            "We could not log you in, because you are not in the Clan Discord Server. If you are a part of the clan in Raid Shadow Legends, please ask one of them admins for an invite link to the Discord Server and try again.",
                            null
                    ));
                });

        // STEP 2: Extract the "Clean" Data
        String cleanDiscordName = memberDto.getDiscordName();
        String cleanAvatarHash = memberDto.getAvatarHash(); // Use this too for consistency
        List<String> roleList = memberDto.getDiscordRoles();
        Set<String> userDiscordRoles = new HashSet<>(roleList);

        // STEP 3: Pass the CLEAN name to the Service (Database Update)
        clanmemberService.linkClanmember(userId, cleanDiscordName, cleanAvatarHash, roleList);

        // --- Security Authorities Setup ---
        Set<SimpleGrantedAuthority> authorities = discordRoleService.getAuthoritiesForRoles(userDiscordRoles, userId);

        boolean hasRequiredRole = userDiscordRoles.contains(discordRoleService.getT1RoleId()) ||
                userDiscordRoles.contains(discordRoleService.getT2RoleId());

        Map<String, Object> updatedAttributes = new HashMap<>(oauth2User.getAttributes());
        updatedAttributes.put("rawDiscordRoleIds", userDiscordRoles);
        updatedAttributes.put("needsRoleWarning", !hasRequiredRole);

        // Optional: Update the map with the clean name so the session has it right away too
        updatedAttributes.put("global_name", cleanDiscordName);

        log.info("Logged in user {} (Discord: {}) has authorities: {}",
                cleanDiscordName, userId, authorities);

        return new DefaultOAuth2User(
                authorities,
                updatedAttributes,
                "id"
        );
    }

    protected OAuth2User fetchUserInfo(OAuth2UserRequest userRequest) {
        return super.loadUser(userRequest);
    }
}