package com.rsl.clansite.service;

import com.rsl.clansite.model.dto.ClanmemberMappingDto;
import com.rsl.clansite.model.entity.ClanmemberMapping;
import com.rsl.clansite.repository.ClanmemberMappingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ClanmemberMappingService {
    private final ClanmemberMappingRepository repository;

    /**
     * Synchronizes a member's name on application startup.
     * Marks the user as an active app user.
     */
    public void syncMember(Long plariumId, String playerName) {
        ClanmemberMapping mapping = repository.findById(plariumId)
                .orElse(new ClanmemberMapping());

        mapping.setPlariumId(plariumId);
        mapping.setPlayerName(playerName);
        mapping.setHasUsedApp(true);

        repository.save(mapping);
    }

    /**
     * Returns the entire roster mapping dictionary for local caching in KloepieBot.
     */
    public Map<Long, String> getRosterMapping() {
        return repository.findAll().stream().collect(Collectors.toMap(
                ClanmemberMapping::getPlariumId,
                ClanmemberMapping::getPlayerName,
                (existing, replacement) -> replacement
        ));
    }

    /**
     * Process admin manual overrides for unknown players found on the siege map.
     * Default hasUsedApp to false for newly discovered accounts.
     */
    public void bulkMapMembers(List<ClanmemberMappingDto> mappings) {
        if (mappings == null || mappings.isEmpty()) {
            return;
        }

        List<ClanmemberMapping> entitiesToSave = mappings.stream().map(dto -> {
            ClanmemberMapping mapping = repository.findById(dto.getPlariumId())
                    .orElseGet(() -> {
                        ClanmemberMapping newMapping = new ClanmemberMapping();
                        newMapping.setPlariumId(dto.getPlariumId());
                        newMapping.setHasUsedApp(false);
                        return newMapping;
                    });

            mapping.setPlayerName(dto.getPlayerName());
            return mapping;
        }).collect(Collectors.toList());

        repository.saveAll(entitiesToSave);
    }
}
