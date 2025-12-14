package com.rsl.clansite.service;

import com.rsl.clansite.model.Clanmember;
import com.rsl.clansite.model.ClanmemberViewData;
import com.rsl.clansite.model.entity.ClanmemberEntity;
import com.rsl.clansite.repository.ClanmemberRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ClanmemberService {
    private final ClanmemberRepository clanmemberRepository;
    private final DiscordRoleService discordRoleService;

    @Autowired
    public ClanmemberService(ClanmemberRepository clanmemberRepository, final DiscordRoleService discordRoleService) {
        this.clanmemberRepository = clanmemberRepository;
        this.discordRoleService = discordRoleService;
    }

    public void linkClanmember(final String discordId, final String linkingName, final String globalName) {
        Optional<ClanmemberEntity> existingMemberById = clanmemberRepository.findByDiscordId(discordId);

        if (existingMemberById.isPresent()) {
            ClanmemberEntity clanmemberEntity = existingMemberById.get();
            clanmemberEntity.setDiscordName(globalName);
            clanmemberRepository.save(clanmemberEntity);
            log.info("Clanmember with Discord ID {} is already linked. Updated Global Name.", discordId);
            return;
        }

        Optional<ClanmemberEntity> unlinkedMember = clanmemberRepository.findByPlayerName(linkingName);

        if (unlinkedMember.isPresent()) {
            ClanmemberEntity clanmemberEntity = unlinkedMember.get();
            clanmemberEntity.setDiscordId(discordId);
            clanmemberEntity.setDiscordName(globalName);
            clanmemberRepository.save(clanmemberEntity);
            log.info("Successfully linked existing Clanmember '{}' with Discord ID {}.", linkingName, discordId);
        } else {
            Clanmember newMember = new Clanmember(
                    globalName,
                    discordId,
                    linkingName,
                    "N/A",
                    "Unassigned",
                    List.of()
            );
            ClanmemberEntity clanmemberEntity = mapClanmemberToEntity(newMember);

            clanmemberRepository.save(clanmemberEntity);
            log.warn("No existing roster entry found for '{}'. Created new 'Unassigned' entry.", linkingName);
        }
    }

    public List<Clanmember> findAllMembers() {
        List<ClanmemberEntity> entities = clanmemberRepository.findAll();

        return mapEntitiesToClanmembers(entities);
    }

    public ClanmemberViewData getUserViewData(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof OAuth2User oauth2User)) {
            return new ClanmemberViewData(null, List.of());
        }

        String globalName = oauth2User.getAttribute("global_name");
        String discordUserName = (globalName != null) ? globalName : "Unknown User";

        List<String> roleNames = List.of("No Discord Roles Found");

        Object rawRolesObject = oauth2User.getAttributes().get("rawDiscordRoleIds");

        if (rawRolesObject instanceof Set) {
            @SuppressWarnings("unchecked")
            Set<String> serverRoleIds = (Set<String>) rawRolesObject;
            final List<String> masterOrder = discordRoleService.getOrderedRoleIds();
            List<String> userRoleIds = new ArrayList<>(serverRoleIds);

            userRoleIds.sort((id1, id2) -> {
                int index1 = masterOrder.indexOf(id1);
                int index2 = masterOrder.indexOf(id2);

                if (index1 == -1 && index2 == -1) return 0;
                if (index1 == -1) return 1;
                if (index2 == -1) return -1;

                return Integer.compare(index1, index2);
            });

            roleNames = userRoleIds.stream()
                    .map(discordRoleService::getRoleName)
                    .toList();
        }

        return new ClanmemberViewData(discordUserName, roleNames);
    }

    private Clanmember mapEntityToClanmember(final ClanmemberEntity clanmemberEntity) {
        if (clanmemberEntity == null) {
            return null;
        }

        return new Clanmember(
                clanmemberEntity.getDiscordName(),
                clanmemberEntity.getDiscordId(),
                clanmemberEntity.getPlayerName(),
                clanmemberEntity.getIngameName(),
                clanmemberEntity.getClanRank(),
                clanmemberEntity.getChampions()
        );
    }

    private ClanmemberEntity mapClanmemberToEntity(Clanmember clanmember) {
        if (clanmember == null) {
            return null;
        }

        ClanmemberEntity entity = new ClanmemberEntity();

        entity.setDiscordName(clanmember.getDiscordName());
        entity.setDiscordId(clanmember.getDiscordId());
        entity.setPlayerName(clanmember.getPlayerName());
        entity.setIngameName(clanmember.getIngameName());
        entity.setClanRank(clanmember.getClanRank());
        entity.setChampions(clanmember.getChampions());

        return entity;
    }

    private List<Clanmember> mapEntitiesToClanmembers(List<ClanmemberEntity> entities) {
        return entities.stream()
                .map(this::mapEntityToClanmember)
                .collect(Collectors.toList());
    }
}
