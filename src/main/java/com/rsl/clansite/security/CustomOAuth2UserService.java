package com.rsl.clansite.security;

import com.rsl.clansite.client.DiscordApiClient;
import com.rsl.clansite.model.dto.NewClanmemberDTO;
import com.rsl.clansite.service.ClanmemberService;
import com.rsl.clansite.service.DiscordRoleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
        String globalName = oauth2User.getAttribute("global_name");
        String avatarHash = oauth2User.getAttribute("avatar");

        NewClanmemberDTO memberDto = discordApiClient.getDiscordMember(userId)
                .orElseThrow(() -> {
                    log.warn("Access Denied for user {}: Not a member of the clan server.", userId);
                    return new OAuth2AuthenticationException(new OAuth2Error(
                            "not_in_guild",
                            "We could not log you in, because you are not in the Clan Discord Server. If you are a part of the clan in Raid Shadow Legends, please ask one of them admins for an invite link to the Discord Server and try again.",
                            null
                    ));
                });

        List<String> roleList = memberDto.getDiscordRoles();
        Set<String> userDiscordRoles = new HashSet<>(roleList);

        clanmemberService.linkClanmember(userId, globalName, avatarHash, roleList);

        Set<SimpleGrantedAuthority> authorities = discordRoleService.getAuthoritiesForRoles(userDiscordRoles, userId);

        boolean hasRequiredRole = userDiscordRoles.contains(discordRoleService.getT1RoleId()) ||
                userDiscordRoles.contains(discordRoleService.getT2RoleId());

        Map<String, Object> updatedAttributes = new HashMap<>(oauth2User.getAttributes());
        updatedAttributes.put("rawDiscordRoleIds", userDiscordRoles);
        updatedAttributes.put("needsRoleWarning", !hasRequiredRole);

        log.info("Logged in user {} has the following roles: {}", globalName, authorities);

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