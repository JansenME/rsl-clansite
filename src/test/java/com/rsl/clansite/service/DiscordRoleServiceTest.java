package com.rsl.clansite.service;

import com.rsl.clansite.client.DiscordApiClient;
import com.rsl.clansite.model.dto.DiscordRoleDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiscordRoleServiceTest {
    @Mock
    private DiscordApiClient discordApiClient;

    @InjectMocks
    private DiscordRoleService discordRoleService;

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
}