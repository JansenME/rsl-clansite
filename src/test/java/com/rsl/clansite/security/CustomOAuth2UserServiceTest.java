package com.rsl.clansite.security;

import com.rsl.clansite.client.DiscordApiClient;
import com.rsl.clansite.model.dto.NewClanmemberDTO;
import com.rsl.clansite.service.ClanmemberService;
import com.rsl.clansite.service.DiscordRoleService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomOAuth2UserServiceTest {
    @Mock
    private ClanmemberService clanmemberService;

    @Mock
    private DiscordApiClient discordApiClient;

    @Mock
    private OAuth2UserRequest userRequest;

    @Spy
    @InjectMocks
    private CustomOAuth2UserService userService;

    @Test
    @DisplayName("loadUser should link account and grant roles when user is in guild")
    void loadUser_ShouldLinkAndGrantRoles_WhenUserInGuild() {
        String discordId = "12345";
        String globalName = "TestUser";

        OAuth2User mockOAuthUser = new DefaultOAuth2User(
                Set.of(),
                Map.of("id", discordId, "global_name", globalName, "avatar", "hash"),
                "id"
        );
        doReturn(mockOAuthUser).when(userService).fetchUserInfo(any());

        NewClanmemberDTO memberDto = new NewClanmemberDTO();
        memberDto.setDiscordRoles(List.of(DiscordRoleService.CLAN_LEADER_ROLE_ID));
        when(discordApiClient.getDiscordMember(discordId)).thenReturn(Optional.of(memberDto));

        OAuth2User result = userService.loadUser(userRequest);

        verify(clanmemberService).linkClanmember(eq(discordId), eq(globalName), any(), anyList());

        Set<String> authorities = result.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        assertTrue(authorities.contains("ROLE_ADMIN"));
        assertTrue(authorities.contains("ROLE_MEMBER"));
    }

    @Test
    @DisplayName("loadUser should throw exception when user is NOT in guild")
    void loadUser_ShouldThrow_WhenUserNotInGuild() {
        String discordId = "999";

        OAuth2User mockOAuthUser = new DefaultOAuth2User(
                Set.of(), Map.of("id", discordId, "global_name", "Stranger"), "id"
        );
        doReturn(mockOAuthUser).when(userService).fetchUserInfo(any());

        when(discordApiClient.getDiscordMember(discordId)).thenReturn(Optional.empty());

        OAuth2AuthenticationException ex = assertThrows(OAuth2AuthenticationException.class,
                () -> userService.loadUser(userRequest));

        assertEquals("not_in_guild", ex.getError().getErrorCode());
        verify(clanmemberService, never()).linkClanmember(any(), any(), any(), any());
    }

    @Test
    @DisplayName("loadUser should propagate RuntimeException from service (System Error)")
    void loadUser_ShouldPropagateServiceException() {
        String discordId = "12345";

        OAuth2User mockOAuthUser = new DefaultOAuth2User(
                Set.of(), Map.of("id", discordId, "global_name", "UnlinkedUser"), "id"
        );
        doReturn(mockOAuthUser).when(userService).fetchUserInfo(any());

        NewClanmemberDTO memberDto = new NewClanmemberDTO();
        memberDto.setDiscordRoles(List.of());
        when(discordApiClient.getDiscordMember(discordId)).thenReturn(Optional.of(memberDto));

        doThrow(new RuntimeException("Database Down")).when(clanmemberService)
                .linkClanmember(any(), any(), any(), any());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> userService.loadUser(userRequest));

        assertEquals("Database Down", ex.getMessage());
    }

    @Test
    @DisplayName("loadUser should grant ROLE_OWNER if Discord ID matches Owner ID")
    void loadUser_ShouldGrantOwnerRole() {
        String ownerId = "270588526267990017";

        OAuth2User mockOAuthUser = new DefaultOAuth2User(
                Set.of(), Map.of("id", ownerId, "global_name", "TheOwner"), "id"
        );
        doReturn(mockOAuthUser).when(userService).fetchUserInfo(any());

        NewClanmemberDTO memberDto = new NewClanmemberDTO();
        memberDto.setDiscordRoles(List.of());
        when(discordApiClient.getDiscordMember(ownerId)).thenReturn(Optional.of(memberDto));

        OAuth2User result = userService.loadUser(userRequest);

        Set<String> authorities = result.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        assertTrue(authorities.contains("ROLE_OWNER"));
    }

    @Test
    @DisplayName("loadUser should grant ROLE_COORDINATOR if user has coordinator role")
    void loadUser_ShouldGrantCoordinatorRole() {
        String discordId = "555";

        OAuth2User mockOAuthUser = new DefaultOAuth2User(
                Set.of(), Map.of("id", discordId, "global_name", "SiegeMaster"), "id"
        );
        doReturn(mockOAuthUser).when(userService).fetchUserInfo(any());

        NewClanmemberDTO memberDto = new NewClanmemberDTO();
        memberDto.setDiscordRoles(List.of(DiscordRoleService.SIEGE_COORDINATOR_ROLE_ID));
        when(discordApiClient.getDiscordMember(discordId)).thenReturn(Optional.of(memberDto));

        OAuth2User result = userService.loadUser(userRequest);

        Set<String> authorities = result.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        assertTrue(authorities.contains("ROLE_COORDINATOR"));
    }
}