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
    private static final String OWNER_DISCORD_ID = "270588526267990017";

    private final ClanmemberService clanmemberService;
    private final DiscordApiClient discordApiClient;

    private static final Set<String> ADMIN_ROLE_IDS = Set.of(
            DiscordRoleService.CLAN_LEADER_ROLE_ID,
            DiscordRoleService.DEPUTY_ROLE_ID
    );

    private static final Set<String> COORDINATOR_ROLE_IDS = Set.of(
            DiscordRoleService.SIEGE_COORDINATOR_ROLE_ID
    );

    private static final Set<String> MEMBER_ROLE_IDS = Set.of(
            DiscordRoleService.T1_ROLE_ID,
            DiscordRoleService.T2_ROLE_ID
    );

    public CustomOAuth2UserService(ClanmemberService clanmemberService, DiscordApiClient discordApiClient) {
        this.clanmemberService = clanmemberService;
        this.discordApiClient = discordApiClient;
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
                            "Access Denied: You must be a member of the clan's Discord server to access this application.",
                            null
                    ));
                });

        List<String> roleList = memberDto.getDiscordRoles();
        Set<String> userDiscordRoles = new HashSet<>(roleList);

        clanmemberService.linkClanmember(userId, globalName, avatarHash, roleList);

        Set<SimpleGrantedAuthority> authorities = mapRolesToAuthorities(userDiscordRoles);

        if (OWNER_DISCORD_ID.equals(userId)) {
            log.info("Granting ROLE_OWNER to Discord ID: {}", userId);
            authorities.add(new SimpleGrantedAuthority("ROLE_OWNER"));
        }

        Map<String, Object> updatedAttributes = new HashMap<>(oauth2User.getAttributes());
        updatedAttributes.put("rawDiscordRoleIds", userDiscordRoles);

        log.info("Logged in user {} has the following roles: {}", globalName, authorities);

        //authorities.clear(); authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        //authorities.clear(); authorities.add(new SimpleGrantedAuthority("ROLE_COORDINATOR"));
        //authorities.clear(); authorities.add(new SimpleGrantedAuthority("ROLE_MEMBER"));

        return new DefaultOAuth2User(
                authorities,
                updatedAttributes,
                "id"
        );
    }

    private Set<SimpleGrantedAuthority> mapRolesToAuthorities(Set<String> userDiscordRoles) {
        Set<SimpleGrantedAuthority> authorities = new HashSet<>();

        if (userDiscordRoles.stream().anyMatch(ADMIN_ROLE_IDS::contains)) {
            authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        }

        if (userDiscordRoles.stream().anyMatch(COORDINATOR_ROLE_IDS::contains)) {
            authorities.add(new SimpleGrantedAuthority("ROLE_COORDINATOR"));
        }

        if (userDiscordRoles.stream().anyMatch(MEMBER_ROLE_IDS::contains)) {
            authorities.add(new SimpleGrantedAuthority("ROLE_MEMBER"));
        }

        return authorities;
    }

    protected OAuth2User fetchUserInfo(OAuth2UserRequest userRequest) {
        return super.loadUser(userRequest);
    }
}
