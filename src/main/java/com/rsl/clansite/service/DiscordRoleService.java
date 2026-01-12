package com.rsl.clansite.service;

import com.rsl.clansite.client.DiscordApiClient;
import com.rsl.clansite.model.dto.DiscordRoleDTO;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class DiscordRoleService {
    @Value("${discord.roles.clan-leader-id}")
    @Getter
    private String clanLeaderRoleId;

    @Value("${discord.roles.deputy-id}")
    @Getter
    private String deputyRoleId;

    @Value("${discord.roles.siege-coordinator-id}")
    @Getter
    private String siegeCoordinatorRoleId;

    @Value("${discord.roles.t1-id}")
    @Getter
    private String t1RoleId;

    @Value("${discord.roles.t2-id}")
    @Getter
    private String t2RoleId;

    private final DiscordApiClient discordApiClient;

    private Map<String, String> roleIdToNameMap = Collections.emptyMap();
    @Getter
    private List<String> orderedRoleIds = Collections.emptyList();

    public DiscordRoleService(DiscordApiClient discordApiClient) {
        this.discordApiClient = discordApiClient;
    }

    @PostConstruct
    public void init() {
        log.info("Fetching and caching Discord role names via API Client.");
        try {
            List<DiscordRoleDTO> rolesList = new ArrayList<>(discordApiClient.getGuildRoles());

            Map<String, String> tempMap = new HashMap<>();

            for (DiscordRoleDTO role : rolesList) {
                tempMap.put(role.getId(), role.getName());
            }

            rolesList.sort(Comparator.comparingInt(DiscordRoleDTO::getPosition).reversed());

            List<String> tempOrderedIds = rolesList.stream()
                    .map(DiscordRoleDTO::getId)
                    .toList();

            this.roleIdToNameMap = Collections.unmodifiableMap(tempMap);
            this.orderedRoleIds = Collections.unmodifiableList(tempOrderedIds);
            log.info("Successfully cached {} Discord roles.", this.roleIdToNameMap.size());
        } catch (Exception e) {
            log.error("Failed to fetch and cache Discord roles. Role display will show IDs.", e);
        }
    }

    public String getRoleName(String roleId) {
        return roleIdToNameMap.getOrDefault(roleId, roleId);
    }

    public List<String> sortRoles(List<String> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return new ArrayList<>();
        }

        List<String> sortedList = new ArrayList<>(roleIds);

        sortedList.sort((id1, id2) -> {
            int index1 = orderedRoleIds.indexOf(id1);
            int index2 = orderedRoleIds.indexOf(id2);

            if (index1 == -1 && index2 == -1) return 0;
            if (index1 == -1) return 1;
            if (index2 == -1) return -1;

            return Integer.compare(index1, index2);
        });

        return sortedList;
    }
}
