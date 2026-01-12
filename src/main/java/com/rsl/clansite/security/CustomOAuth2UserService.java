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
    @Value("${discord.kloep-id}")
    private String kloepDiscordId;

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
                            "Access Denied: You must be a member of the clan's Discord server to access this application.",
                            null
                    ));
                });

        List<String> roleList = memberDto.getDiscordRoles();
        Set<String> userDiscordRoles = new HashSet<>(roleList);

        clanmemberService.linkClanmember(userId, globalName, avatarHash, roleList);

        Set<SimpleGrantedAuthority> authorities = mapRolesToAuthorities(userDiscordRoles);

        if (kloepDiscordId.equals(userId)) {
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

        if (userDiscordRoles.contains(discordRoleService.getClanLeaderRoleId()) ||
                userDiscordRoles.contains(discordRoleService.getDeputyRoleId())) {
            authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        }

        if (userDiscordRoles.contains(discordRoleService.getSiegeCoordinatorRoleId())) {
            authorities.add(new SimpleGrantedAuthority("ROLE_COORDINATOR"));
        }

        if (userDiscordRoles.contains(discordRoleService.getT1RoleId()) ||
                userDiscordRoles.contains(discordRoleService.getT2RoleId())) {
            authorities.add(new SimpleGrantedAuthority("ROLE_MEMBER"));
        }

        return authorities;
    }

    protected OAuth2User fetchUserInfo(OAuth2UserRequest userRequest) {
        return super.loadUser(userRequest);
    }
}
