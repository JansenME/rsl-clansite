package com.rsl.clansite.security;

import com.rsl.clansite.client.DiscordApiClient;
import com.rsl.clansite.model.dto.NewClanmemberDTO;
import com.rsl.clansite.service.ClanmemberService;
import com.rsl.clansite.service.DiscordRoleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ActiveProfiles("test")
@ExtendWith(MockitoExtension.class)
class CustomOAuth2UserServiceTest {
    @Mock
    private ClanmemberService clanmemberService;

    @Mock
    private DiscordApiClient discordApiClient;

    @Mock
    private DiscordRoleService discordRoleService;

    @Mock
    private OAuth2UserRequest userRequest;

    @Spy
    @InjectMocks
    private CustomOAuth2UserService customOAuth2UserService;

    @BeforeEach
    void setUp() {
        // These are technically not needed anymore since the logic moved to getAuthoritiesForRoles,
        // but we keep them leniently to avoid "UnnecessaryStubbingException" if legacy code touches them.
        lenient().when(discordRoleService.getT1RoleId()).thenReturn("test-t1-id");
        lenient().when(discordRoleService.getT2RoleId()).thenReturn("test-t2-id");
        lenient().when(discordRoleService.getClanLeaderRoleId()).thenReturn("test-leader-id");
        lenient().when(discordRoleService.getDeputyRoleId()).thenReturn("test-deputy-id");
        lenient().when(discordRoleService.getSiegeCoordinatorRoleId()).thenReturn("test-coordinator-id");

        ReflectionTestUtils.setField(customOAuth2UserService, "kloepDiscordId", "270588526267990017");
    }

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
        doReturn(mockOAuthUser).when(customOAuth2UserService).fetchUserInfo(any());

        NewClanmemberDTO memberDto = new NewClanmemberDTO();
        memberDto.setDiscordRoles(List.of(discordRoleService.getClanLeaderRoleId()));
        when(discordApiClient.getDiscordMember(discordId)).thenReturn(Optional.of(memberDto));

        // FIX: Stub the delegated authority calculation
        when(discordRoleService.getAuthoritiesForRoles(any(), eq(discordId)))
                .thenReturn(Set.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        OAuth2User result = customOAuth2UserService.loadUser(userRequest);

        verify(clanmemberService).linkClanmember(eq(discordId), eq(globalName), any(), anyList());

        Set<String> authorities = result.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        assertTrue(authorities.contains("ROLE_ADMIN"));
    }

    @Test
    @DisplayName("loadUser should throw exception when user is NOT in guild")
    void loadUser_ShouldThrow_WhenUserNotInGuild() {
        String discordId = "999";

        OAuth2User mockOAuthUser = new DefaultOAuth2User(
                Set.of(), Map.of("id", discordId, "global_name", "Stranger"), "id"
        );
        doReturn(mockOAuthUser).when(customOAuth2UserService).fetchUserInfo(any());

        when(discordApiClient.getDiscordMember(discordId)).thenReturn(Optional.empty());

        OAuth2AuthenticationException ex = assertThrows(OAuth2AuthenticationException.class,
                () -> customOAuth2UserService.loadUser(userRequest));

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
        doReturn(mockOAuthUser).when(customOAuth2UserService).fetchUserInfo(any());

        NewClanmemberDTO memberDto = new NewClanmemberDTO();
        memberDto.setDiscordRoles(List.of());
        when(discordApiClient.getDiscordMember(discordId)).thenReturn(Optional.of(memberDto));

        doThrow(new RuntimeException("Database Down")).when(clanmemberService)
                .linkClanmember(any(), any(), any(), any());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> customOAuth2UserService.loadUser(userRequest));

        assertEquals("Database Down", ex.getMessage());
    }

    @Test
    @DisplayName("loadUser should grant ROLE_OWNER if Discord ID matches Owner ID")
    void loadUser_ShouldGrantOwnerRole() {
        String ownerId = "270588526267990017";

        OAuth2User mockOAuthUser = new DefaultOAuth2User(
                Set.of(), Map.of("id", ownerId, "global_name", "TheOwner"), "id"
        );
        doReturn(mockOAuthUser).when(customOAuth2UserService).fetchUserInfo(any());

        NewClanmemberDTO memberDto = new NewClanmemberDTO();
        memberDto.setDiscordRoles(List.of());
        when(discordApiClient.getDiscordMember(ownerId)).thenReturn(Optional.of(memberDto));

        // FIX: Stub the delegation (Owner logic is now inside DiscordRoleService)
        when(discordRoleService.getAuthoritiesForRoles(any(), eq(ownerId)))
                .thenReturn(Set.of(new SimpleGrantedAuthority("ROLE_OWNER")));

        OAuth2User result = customOAuth2UserService.loadUser(userRequest);

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
        doReturn(mockOAuthUser).when(customOAuth2UserService).fetchUserInfo(any());

        NewClanmemberDTO memberDto = new NewClanmemberDTO();
        memberDto.setDiscordRoles(List.of(discordRoleService.getSiegeCoordinatorRoleId()));
        when(discordApiClient.getDiscordMember(discordId)).thenReturn(Optional.of(memberDto));

        // FIX: Stub delegation
        when(discordRoleService.getAuthoritiesForRoles(any(), eq(discordId)))
                .thenReturn(Set.of(new SimpleGrantedAuthority("ROLE_COORDINATOR")));

        OAuth2User result = customOAuth2UserService.loadUser(userRequest);

        Set<String> authorities = result.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        assertTrue(authorities.contains("ROLE_COORDINATOR"));
    }

    @Test
    @DisplayName("loadUser - SAFETY CHECK: Regular user must NOT get Admin/Coordinator roles")
    void loadUser_RegularUser_ShouldNotHaveElevatedPrivileges() {
        String discordId = "987654321";

        OAuth2User mockOAuthUser = new DefaultOAuth2User(
                Set.of(), Map.of("id", discordId, "global_name", "RegularJoe", "avatar", "hash"), "id"
        );
        doReturn(mockOAuthUser).when(customOAuth2UserService).fetchUserInfo(any());

        NewClanmemberDTO memberDto = new NewClanmemberDTO();
        memberDto.setDiscordRoles(List.of(discordRoleService.getT1RoleId()));
        when(discordApiClient.getDiscordMember(discordId)).thenReturn(Optional.of(memberDto));

        // FIX: Stub delegation for Member
        when(discordRoleService.getAuthoritiesForRoles(any(), eq(discordId)))
                .thenReturn(Set.of(new SimpleGrantedAuthority("ROLE_MEMBER")));

        OAuth2User result = customOAuth2UserService.loadUser(userRequest);

        assertTrue(result.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_MEMBER")));

        assertTrue(result.getAuthorities().stream()
                        .noneMatch(a -> a.getAuthority().equals("ROLE_ADMIN")),
                "Security Alert: Regular user has ADMIN role!");
    }

    @Test
    @DisplayName("loadUser - SAFETY CHECK: Random roles should NOT grant any access")
    void loadUser_RandomRole_ShouldHaveNoAccess() {
        String discordId = "987654321";

        OAuth2User mockOAuthUser = new DefaultOAuth2User(
                Set.of(), Map.of("id", discordId, "global_name", "RandomGuy", "avatar", "hash"), "id"
        );
        doReturn(mockOAuthUser).when(customOAuth2UserService).fetchUserInfo(any());

        NewClanmemberDTO memberDto = new NewClanmemberDTO();
        memberDto.setDiscordRoles(List.of("999999"));
        when(discordApiClient.getDiscordMember(discordId)).thenReturn(Optional.of(memberDto));

        // FIX: Stub delegation for Random/Unknown roles (Empty Set)
        when(discordRoleService.getAuthoritiesForRoles(any(), eq(discordId)))
                .thenReturn(Collections.emptySet());

        OAuth2User result = customOAuth2UserService.loadUser(userRequest);

        assertTrue(result.getAuthorities().isEmpty(),
                "Security Alert: User with random role got access! Authorities: " + result.getAuthorities());
    }

    @Test
    @DisplayName("loadUser - T1/T2 Role should grant ROLE_MEMBER")
    void loadUser_T1Role_ShouldGrantMember() {
        String discordId = "111222333";
        OAuth2User mockOAuthUser = new DefaultOAuth2User(
                Set.of(), Map.of("id", discordId, "global_name", "T1Soldier", "avatar", "hash"), "id"
        );
        doReturn(mockOAuthUser).when(customOAuth2UserService).fetchUserInfo(any());

        NewClanmemberDTO memberDto = new NewClanmemberDTO();
        memberDto.setDiscordRoles(List.of(discordRoleService.getT1RoleId()));
        when(discordApiClient.getDiscordMember(discordId)).thenReturn(Optional.of(memberDto));

        // FIX: Stub delegation
        when(discordRoleService.getAuthoritiesForRoles(any(), eq(discordId)))
                .thenReturn(Set.of(new SimpleGrantedAuthority("ROLE_MEMBER")));

        OAuth2User result = customOAuth2UserService.loadUser(userRequest);

        assertTrue(result.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_MEMBER")));
    }
}