package com.rsl.clansite.service;

import com.rsl.clansite.client.DiscordApiClient;
import com.rsl.clansite.model.dto.DiscordRoleDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiscordRoleServiceTest {
    @Mock
    private DiscordApiClient discordApiClient;

    @InjectMocks
    private DiscordRoleService discordRoleService;

    private final String LEADER_ID = "leader-123";
    private final String DEPUTY_ID = "deputy-456";
    private final String COORD_ID = "coord-789";
    private final String T1_ID = "t1-111";
    private final String T2_ID = "t2-222";
    private final String OWNER_ID = "owner-999";

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(discordRoleService, "clanLeaderRoleId", LEADER_ID);
        ReflectionTestUtils.setField(discordRoleService, "deputyRoleId", DEPUTY_ID);
        ReflectionTestUtils.setField(discordRoleService, "siegeCoordinatorRoleId", COORD_ID);
        ReflectionTestUtils.setField(discordRoleService, "t1RoleId", T1_ID);
        ReflectionTestUtils.setField(discordRoleService, "t2RoleId", T2_ID);
        ReflectionTestUtils.setField(discordRoleService, "kloepDiscordId", OWNER_ID);
    }

    @Test
    @DisplayName("init should fetch, cache and sort roles by position descending")
    void init_ShouldCacheAndSortRoles() {
        DiscordRoleDTO role1 = new DiscordRoleDTO();
        role1.setId("10");
        role1.setName("Leader");
        role1.setPosition(100);

        DiscordRoleDTO role2 = new DiscordRoleDTO();
        role2.setId("20");
        role2.setName("Soldier");
        role2.setPosition(50);

        when(discordApiClient.getGuildRoles()).thenReturn(List.of(role2, role1));

        discordRoleService.init();

        assertEquals("Leader", discordRoleService.getRoleName("10"));
        assertEquals("Soldier", discordRoleService.getRoleName("20"));

        List<String> orderedIds = discordRoleService.getOrderedRoleIds();
        assertEquals(2, orderedIds.size());
        assertEquals("10", orderedIds.get(0));
        assertEquals("20", orderedIds.get(1));
    }

    @Test
    @DisplayName("init should handle API failure gracefully (log error, empty cache)")
    void init_ShouldHandleApiFailure() {
        when(discordApiClient.getGuildRoles()).thenThrow(new RuntimeException("API Down"));

        discordRoleService.init();

        assertTrue(discordRoleService.getOrderedRoleIds().isEmpty());
        assertEquals("999", discordRoleService.getRoleName("999"));
    }

    @Test
    @DisplayName("getRoleName should return ID if role is not in cache")
    void getRoleName_ShouldReturnId_WhenUnknown() {
        String result = discordRoleService.getRoleName("unknown_id");
        assertEquals("unknown_id", result);
    }

    @Test
    @DisplayName("sortRoles should sort according to cached Master List")
    void sortRoles_ShouldSortCorrectly() {
        DiscordRoleDTO high = new DiscordRoleDTO(); high.setId("high"); high.setPosition(10);
        DiscordRoleDTO mid = new DiscordRoleDTO(); mid.setId("mid"); mid.setPosition(5);
        DiscordRoleDTO low = new DiscordRoleDTO(); low.setId("low"); low.setPosition(1);

        when(discordApiClient.getGuildRoles()).thenReturn(List.of(low, high, mid));
        discordRoleService.init();

        List<String> input = List.of("unknown", "low", "high");
        List<String> result = discordRoleService.sortRoles(input);

        assertEquals("high", result.get(0));
        assertEquals("low", result.get(1));
        assertEquals("unknown", result.get(2));
    }

    @Test
    @DisplayName("sortRoles should return empty list for null/empty input")
    void sortRoles_ShouldHandleEmptyInput() {
        assertTrue(discordRoleService.sortRoles(null).isEmpty());
        assertTrue(discordRoleService.sortRoles(List.of()).isEmpty());
    }

    @Test
    @DisplayName("sortRoles should handle case where neither role is known (stable sort)")
    void sortRoles_ShouldKeepOrder_WhenBothUnknown() {
        List<String> input = List.of("unknownA", "unknownB");
        List<String> result = discordRoleService.sortRoles(input);

        assertEquals("unknownA", result.get(0));
        assertEquals("unknownB", result.get(1));
    }

    @Test
    @DisplayName("Leader Role should map to ROLE_ADMIN")
    void getAuthorities_Leader_ShouldBeAdmin() {
        List<String> roles = List.of(LEADER_ID);
        Set<SimpleGrantedAuthority> result = discordRoleService.getAuthoritiesForRoles(roles, "random-user");

        assertHasAuthority("ROLE_ADMIN", result);
    }

    @Test
    @DisplayName("Deputy Role should map to ROLE_ADMIN")
    void getAuthorities_Deputy_ShouldBeAdmin() {
        List<String> roles = List.of(DEPUTY_ID);
        Set<SimpleGrantedAuthority> result = discordRoleService.getAuthoritiesForRoles(roles, "random-user");

        assertHasAuthority("ROLE_ADMIN", result);
    }

    @Test
    @DisplayName("Coordinator Role should map to ROLE_COORDINATOR")
    void getAuthorities_Coordinator_ShouldBeCoordinator() {
        List<String> roles = List.of(COORD_ID);
        Set<SimpleGrantedAuthority> result = discordRoleService.getAuthoritiesForRoles(roles, "random-user");

        assertHasAuthority("ROLE_COORDINATOR", result);
    }

    @Test
    @DisplayName("T1/T2 Roles should map to ROLE_MEMBER")
    void getAuthorities_Members_ShouldBeMember() {
        // Test T1
        Set<SimpleGrantedAuthority> t1Result = discordRoleService.getAuthoritiesForRoles(List.of(T1_ID), "random-user");
        assertHasAuthority("ROLE_MEMBER", t1Result);

        // Test T2
        Set<SimpleGrantedAuthority> t2Result = discordRoleService.getAuthoritiesForRoles(List.of(T2_ID), "random-user");
        assertHasAuthority("ROLE_MEMBER", t2Result);
    }

    @Test
    @DisplayName("Owner ID should map to ROLE_OWNER (and ADMIN/MEMBER if configured)")
    void getAuthorities_OwnerId_ShouldBeOwner() {
        // Owner usually has no roles in the list, or random roles, but the ID matches
        List<String> roles = Collections.emptyList();

        Set<SimpleGrantedAuthority> result = discordRoleService.getAuthoritiesForRoles(roles, OWNER_ID);

        assertHasAuthority("ROLE_OWNER", result);
    }

    @Test
    @DisplayName("Multiple Roles should result in Multiple Authorities")
    void getAuthorities_MixedRoles_ShouldHaveAll() {
        // A user who is a Coordinator AND a T1 Member
        List<String> roles = List.of(COORD_ID, T1_ID);

        Set<SimpleGrantedAuthority> result = discordRoleService.getAuthoritiesForRoles(roles, "random-user");

        assertHasAuthority("ROLE_COORDINATOR", result);
        assertHasAuthority("ROLE_MEMBER", result);
        assertHasAuthority("ROLE_USER", result);
        assertEquals(3, result.size());
    }

    @Test
    @DisplayName("Unknown Roles should return ROLE_USER only")
    void getAuthorities_UnknownRoles_ShouldReturnDefault() {
        List<String> roles = List.of("unknown-1", "unknown-2");

        Set<SimpleGrantedAuthority> result = discordRoleService.getAuthoritiesForRoles(roles, "random-user");

        assertHasAuthority("ROLE_USER", result);
        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("Null Role List should return ROLE_USER only (No NPE)")
    void getAuthorities_NullList_ShouldReturnDefault() {
        Set<SimpleGrantedAuthority> result = discordRoleService.getAuthoritiesForRoles(null, "random-user");

        assertHasAuthority("ROLE_USER", result);
        assertEquals(1, result.size());
    }

    private void assertHasAuthority(String expectedAuth, Set<SimpleGrantedAuthority> authorities) {
        Set<String> authStrings = authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        assertTrue(authStrings.contains(expectedAuth),
                "Expected authorities to contain " + expectedAuth + " but found: " + authStrings);
    }
}