package com.rsl.clansite.service;

import com.rsl.clansite.exceptions.ChampionSaveException;
import com.rsl.clansite.model.Aura;
import com.rsl.clansite.model.BaseStats;
import com.rsl.clansite.model.Champion;
import com.rsl.clansite.model.dto.ChampionEntryDTO;
import com.rsl.clansite.model.dto.DataHealthDTO;
import com.rsl.clansite.model.entity.ChampionEntity;
import com.rsl.clansite.model.enums.AuditAction;
import com.rsl.clansite.model.enums.Rarity;
import com.rsl.clansite.repository.ChampionRepository;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
public class ChampionsService {
    private final ChampionRepository championRepository;
    private final CommonsService commonsService;
    private final AuditLogService auditLogService;
    private final TargetService targetService;

    @Autowired
    public ChampionsService(final ChampionRepository championRepository,
                            final CommonsService commonsService,
                            final AuditLogService auditLogService,
                            final TargetService targetService) {
        this.championRepository = championRepository;
        this.commonsService = commonsService;
        this.auditLogService = auditLogService;
        this.targetService = targetService;
    }

    public List<Champion> getAllChampions() {
        return mapEntitiesToChampions(championRepository.findAll());
    }

    public List<Champion> getChampionsByIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        List<ObjectId> objectIds = ids.stream()
                .filter(ObjectId::isValid)
                .map(ObjectId::new)
                .toList();

        List<ChampionEntity> entities = championRepository.findAllById(objectIds);
        return mapEntitiesToChampions(entities);
    }

    public DataHealthDTO getDataHealth() {
        Map<Rarity, Integer> missingPerRarity = new HashMap<>();
        int totalMissing = 0;

        for (Rarity rarity : Rarity.values()) {
            int target = targetService.getGlobalTargetForRarity(rarity);
            if (target <= 0) continue;

            int current = championRepository.countByRarity(rarity);
            int diff = target - current;

            if (diff > 0) {
                missingPerRarity.put(rarity, diff);
                totalMissing += diff;
            }
        }

        boolean isHealthy = totalMissing == 0;
        return new DataHealthDTO(isHealthy, totalMissing, missingPerRarity);
    }

    public void saveNewChampion(final ChampionEntryDTO dto, Authentication authentication) throws ChampionSaveException {
        saveNewChampion(dto, authentication, "Created new champion manually");
    }

    public void saveNewChampion(final ChampionEntryDTO dto, Authentication authentication, String auditDetails) throws ChampionSaveException {
        if(!StringUtils.hasText(dto.getName())) {
            throw new ChampionSaveException("Champion name cannot be empty.");
        }

        Optional<ChampionEntity> existing = championRepository.findByNameIgnoreCase(dto.getName());
        if (existing.isPresent()) {
            throw new ChampionSaveException("Champion name '" + dto.getName() + "' is already taken.");
        }

        ChampionEntity entity = mapDtoToEntity(dto);

        try {
            championRepository.save(entity);
            auditLogService.logAction(
                    authentication,
                    AuditAction.CHAMPION_ADD,
                    entity.getName(),
                    auditDetails
            );

        } catch (Exception e) {
            throw new ChampionSaveException("Failed to save champion: " + e.getMessage());
        }
    }

    public ChampionEntity getChampionById(String id) {
        if (!ObjectId.isValid(id)) {
            throw new IllegalArgumentException("Invalid Champion ID: " + id);
        }

        return championRepository.findById(new ObjectId(id))
                .orElseThrow(() -> new RuntimeException("Champion not found with ID: " + id));
    }

    public ChampionEntryDTO getChampionForEdit(String id) {
        ChampionEntity entity = getChampionById(id);

        ChampionEntryDTO dto = new ChampionEntryDTO();

        dto.setId(entity.getId().toHexString());
        dto.setName(entity.getName());
        dto.setImagename(entity.getImagename());
        dto.setCurrentImageName(entity.getImagename());
        dto.setArenaScore(entity.getArenaScore());

        dto.setRarity(entity.getRarity());
        dto.setType(entity.getType());
        dto.setAffinity(entity.getAffinity());
        dto.setFaction(entity.getFaction());

        if (entity.getBaseStats() != null) {
            com.rsl.clansite.model.BaseStats stats = entity.getBaseStats();
            dto.setHp(stats.getHp());
            dto.setAttack(stats.getAttack());
            dto.setDefense(stats.getDefense());
            dto.setSpeed(stats.getSpeed());
            dto.setCriticalRate(stats.getCriticalRate());
            dto.setCriticalDamage(stats.getCriticalDamage());
            dto.setResistance(stats.getResistance());
            dto.setAccuracy(stats.getAccuracy());
        }

        if (entity.getAura() != null) {
            dto.setAuraExists(true);
            dto.setStat(entity.getAura().getStat());
            dto.setLocation(entity.getAura().getLocation());
            dto.setAmount(entity.getAura().getAmount());
            dto.setPercentageAura(entity.getAura().isPercentage());
        } else {
            dto.setAuraExists(false);
        }

        return dto;
    }

    public void updateChampion(String id, ChampionEntryDTO dto, Authentication authentication) throws ChampionSaveException {
        ChampionEntity entity = getChampionById(id);

        Optional<ChampionEntity> existing = championRepository.findByNameIgnoreCase(dto.getName());
        if (existing.isPresent() && !existing.get().getId().toHexString().equals(id)) {
            throw new ChampionSaveException("Champion name '" + dto.getName() + "' is already taken.");
        }

        String changes = generateDiff(entity, dto);

        entity.setName(dto.getName());
        entity.setRarity(dto.getRarity());
        entity.setType(dto.getType());
        entity.setAffinity(dto.getAffinity());
        entity.setFaction(dto.getFaction());
        entity.setArenaScore(dto.getArenaScore());

        String generatedFileName = commonsService.generateImageFilename(dto.getName());
        entity.setImagename(generatedFileName);

        entity.setBaseStats(new BaseStats(
                dto.getHp(), dto.getAttack(), dto.getDefense(), dto.getSpeed(),
                dto.getCriticalRate(), dto.getCriticalDamage(), dto.getResistance(), dto.getAccuracy()
        ));

        if (dto.isAuraExists()) {
            entity.setAura(new Aura(dto.isPercentageAura(), dto.getAmount(), dto.getStat(), dto.getLocation()));
        } else {
            entity.setAura(null);
        }

        championRepository.save(entity);
        auditLogService.logAction(
                authentication,
                AuditAction.CHAMPION_UPDATE,
                entity.getName(),
                changes.isEmpty() ? "Updated champion (No changes detected)" : "Updates: " + changes
        );
    }

    public void deleteChampion(String id, Authentication authentication) {
        ChampionEntity entity = getChampionById(id);

        championRepository.delete(entity);

        auditLogService.logAction(
                authentication,
                AuditAction.CHAMPION_DELETE,
                entity.getName(),
                "Deleted champion manually"
        );
    }

    private String generateDiff(ChampionEntity old, ChampionEntryDTO newly) {
        List<String> diffs = new ArrayList<>();

        if (!old.getName().equals(newly.getName())) {
            diffs.add("Name: " + old.getName() + "->" + newly.getName());
        }
        if (old.getRarity() != newly.getRarity()) {
            diffs.add("Rarity: " + old.getRarity() + "->" + newly.getRarity());
        }
        if (old.getType() != newly.getType()) {
            diffs.add("Type: " + old.getType() + "->" + newly.getType());
        }
        if (old.getAffinity() != newly.getAffinity()) {
            diffs.add("Affinity: " + old.getAffinity() + "->" + newly.getAffinity());
        }
        if (old.getFaction() != newly.getFaction()) {
            diffs.add("Faction: " + old.getFaction() + "->" + newly.getFaction());
        }
        if (!Objects.equals(old.getArenaScore(), newly.getArenaScore())) {
            diffs.add("ArenaScore: " + old.getArenaScore() + "->" + newly.getArenaScore());
        }

        BaseStats stats = old.getBaseStats();
        if (stats != null) {
            if (stats.getHp() != newly.getHp()) diffs.add("HP: " + stats.getHp() + "->" + newly.getHp());
            if (stats.getAttack() != newly.getAttack()) diffs.add("Atk: " + stats.getAttack() + "->" + newly.getAttack());
            if (stats.getDefense() != newly.getDefense()) diffs.add("Def: " + stats.getDefense() + "->" + newly.getDefense());
            if (stats.getSpeed() != newly.getSpeed()) diffs.add("Spd: " + stats.getSpeed() + "->" + newly.getSpeed());
            if (stats.getCriticalRate() != newly.getCriticalRate()) diffs.add("C.Rate: " + stats.getCriticalRate() + "->" + newly.getCriticalRate());
            if (stats.getCriticalDamage() != newly.getCriticalDamage()) diffs.add("C.Dmg: " + stats.getCriticalDamage() + "->" + newly.getCriticalDamage());
            if (stats.getResistance() != newly.getResistance()) diffs.add("Res: " + stats.getResistance() + "->" + newly.getResistance());
            if (stats.getAccuracy() != newly.getAccuracy()) diffs.add("Acc: " + stats.getAccuracy() + "->" + newly.getAccuracy());
        }

        boolean oldHasAura = old.getAura() != null;
        if (oldHasAura != newly.isAuraExists()) {
            diffs.add("Aura: " + (oldHasAura ? "Removed" : "Added"));
        } else if (oldHasAura) {
            Aura oldAura = old.getAura();
            if (oldAura.getAmount() != newly.getAmount() || oldAura.getStat() != newly.getStat()) {
                diffs.add("Aura Changed");
            }
        }

        return String.join(", ", diffs);
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
                championEntity.getId().toHexString(),
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