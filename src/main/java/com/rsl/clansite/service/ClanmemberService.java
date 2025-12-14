package com.rsl.clansite.service;

import com.rsl.clansite.model.Clanmember;
import com.rsl.clansite.model.entity.ClanmemberEntity;
import com.rsl.clansite.repository.ClanmemberRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ClanmemberService {
    private final ClanmemberRepository clanmemberRepository;

    @Autowired
    public ClanmemberService(ClanmemberRepository clanmemberRepository) {
        this.clanmemberRepository = clanmemberRepository;
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
