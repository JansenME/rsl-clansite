package com.rsl.clansite.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rsl.clansite.exceptions.ChampionSaveException;
import com.rsl.clansite.model.Aura;
import com.rsl.clansite.model.BaseStats;
import com.rsl.clansite.model.Champion;
import com.rsl.clansite.model.dto.ChampionEntryDTO;
import com.rsl.clansite.model.entity.ChampionEntity;
import com.rsl.clansite.repository.ChampionRepository;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
public class ChampionsService {
    @Value("classpath:champions.json")
    private Resource championsBackupFile;

    private final ChampionRepository championRepository;

    @Autowired
    public ChampionsService(final ChampionRepository championRepository) {
        this.championRepository = championRepository;
    }

    public List<Champion> getAllChampions() {
        return mapEntitiesToChampions(championRepository.findAll());
    }

    public List<ChampionEntity> getAllChampionsEntityList() {
        return championRepository.findAll();
    }

    public List<ChampionEntity> restoreChampionsFromBackup() {
        try {
            if (!championsBackupFile.exists()) {
                throw new RuntimeException("Backup file champions.json not found in resources!");
            }

            ObjectMapper mapper = new ObjectMapper();
            List<ChampionEntity> backupList = mapper.readValue(
                    championsBackupFile.getInputStream(),
                    new TypeReference<List<ChampionEntity>>(){}
            );

            championRepository.deleteAll();
            championRepository.saveAll(backupList);

            return backupList;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load backup file: " + e.getMessage(), e);
        }
    }

    public void saveNewChampion(final ChampionEntryDTO dto) throws ChampionSaveException {
        if(!StringUtils.hasText(dto.getName())) {
            throw new ChampionSaveException("Champion name cannot be empty.");
        }

        ChampionEntity entity = mapDtoToEntity(dto);

        try {
            championRepository.save(entity);

        } catch (Exception e) {
            throw new ChampionSaveException("Failed to save champion: " + e.getMessage());
        }
    }

    private ChampionEntity mapDtoToEntity(final ChampionEntryDTO dto) {
        return new ChampionEntity(
                ObjectId.get(),
                dto.getName(),
                dto.getRarity(),
                dto.getType(),
                dto.getAffinity(),
                dto.getFaction(),
                getBaseStatsFromDto(dto),
                getAuraFromDTO(dto),
                dto.getArenaScore(),
                dto.getName().toLowerCase().replace(" ", "-") + ".png"
        );
    }

    private BaseStats getBaseStatsFromDto(final ChampionEntryDTO dto) {
        return new BaseStats(
                dto.getHp(),
                dto.getAttack(),
                dto.getDefense(),
                dto.getSpeed(),
                dto.getCriticalRate(),
                dto.getCriticalDamage(),
                dto.getResistance(),
                dto.getAccuracy()
        );
    }

    private Aura getAuraFromDTO(final ChampionEntryDTO dto) {
        if (!dto.isAuraExists()) {
            return null;
        }

        return new Aura(
                dto.isPercentageAura(),
                dto.getAmount(),
                dto.getStat(),
                dto.getLocation()
        );
    }

    private List<Champion> mapEntitiesToChampions(final List<ChampionEntity> championEntities) {
        return championEntities.stream()
                .map(this::mapEntityToChampion)
                .sorted(Comparator.comparing(Champion::getName))
                .toList();
    }

    private Champion mapEntityToChampion(final ChampionEntity championEntity) {
        return new Champion(
                championEntity.getName(),
                championEntity.getRarity(),
                championEntity.getType(),
                championEntity.getAffinity(),
                championEntity.getFaction(),
                championEntity.getBaseStats(),
                championEntity.getAura(),
                championEntity.getArenaScore(),
                championEntity.getImagename()
        );
    }
}