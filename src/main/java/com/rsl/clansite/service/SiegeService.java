package com.rsl.clansite.service;

import com.rsl.clansite.model.OwnedChampion;
import com.rsl.clansite.model.SiegeStructure;
import com.rsl.clansite.model.entity.ClanmemberEntity;
import com.rsl.clansite.model.entity.SiegeEntity;
import com.rsl.clansite.model.enums.AuditAction;
import com.rsl.clansite.model.enums.ClanGroup;
import com.rsl.clansite.model.enums.SiegeStatus;
import com.rsl.clansite.model.enums.SiegeStructureType;
import com.rsl.clansite.repository.ClanmemberRepository;
import com.rsl.clansite.repository.SiegeRepository;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SiegeService {

    private final SiegeRepository siegeRepository;
    private final ClanmemberRepository clanmemberRepository;
    private final AuditLogService auditLogService;

    public SiegeService(SiegeRepository siegeRepository,
                        ClanmemberRepository clanmemberRepository,
                        AuditLogService auditLogService) {
        this.siegeRepository = siegeRepository;
        this.clanmemberRepository = clanmemberRepository;
        this.auditLogService = auditLogService;
    }

    public Optional<SiegeEntity> getActiveSiege(ClanGroup clanGroup) {
        return siegeRepository.findFirstByClanGroupAndStatusNot(clanGroup, SiegeStatus.FINISHED);
    }

    @Transactional
    public void checkAndAdvanceState(ClanGroup clanGroup) {
        Optional<SiegeEntity> siegeOpt = getActiveSiege(clanGroup);

        if (siegeOpt.isEmpty()) {
            log.info("[{}] No active siege found during check. Bootstrapping new cycle.", clanGroup);
            createNextSiege(clanGroup, LocalDateTime.now());
            return;
        }

        SiegeEntity siege = siegeOpt.get();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startDate = siege.getStartDate();

        long hoursSinceStart = ChronoUnit.HOURS.between(startDate, now);
        long daysSinceStart = ChronoUnit.DAYS.between(startDate, now);

        if (siege.getStatus() == SiegeStatus.PREP) {
            if (hoursSinceStart >= 288) {
                log.info("[{}] Switching PREP -> MATCHMAKING (Hours: {})", clanGroup, hoursSinceStart);
                siege.setStatus(SiegeStatus.MATCHMAKING);
                siege.setLastModified(now);
                siegeRepository.save(siege);
                auditLogService.logSystemAction(AuditAction.SIEGE_SYSTEM_EVENT, clanGroup.name(), "Auto-advanced to MATCHMAKING");
            }
        }
        else if (siege.getStatus() == SiegeStatus.MATCHMAKING) {
            if (hoursSinceStart >= 292) {
                log.info("[{}] Switching MATCHMAKING -> BATTLE (Hours: {})", clanGroup, hoursSinceStart);
                siege.setStatus(SiegeStatus.BATTLE);
                siege.setLastModified(now);
                siegeRepository.save(siege);
                auditLogService.logSystemAction(AuditAction.SIEGE_SYSTEM_EVENT, clanGroup.name(), "Auto-advanced to BATTLE");
            }
        }
        else if (siege.getStatus() == SiegeStatus.BATTLE) {
            if (daysSinceStart >= 14) {
                log.info("[{}] Siege Cycle Complete ({} days). Rotating...", clanGroup, daysSinceStart);
                finishActiveSiege(clanGroup);
                LocalDateTime nextCycleStart = startDate.plusDays(14);
                createNextSiege(clanGroup, nextCycleStart);
            }
        }
    }

    @Transactional
    public void finishActiveSiege(ClanGroup clanGroup) {
        getActiveSiege(clanGroup).ifPresent(siege -> {
            log.info("Finishing active siege for {} (ID: {})", clanGroup, siege.getId());
            siege.setStatus(SiegeStatus.FINISHED);
            siege.setLastModified(LocalDateTime.now());
            siegeRepository.save(siege);
            auditLogService.logSystemAction(AuditAction.SIEGE_SYSTEM_EVENT, clanGroup.name(), "Siege Cycle Finished");
        });
    }

    @Transactional
    public SiegeEntity createNextSiege(ClanGroup clanGroup, LocalDateTime startDate) {
        finishActiveSiege(clanGroup);

        log.info("Creating new PREP siege for {} starting at {}", clanGroup, startDate);

        SiegeEntity newSiege = new SiegeEntity(clanGroup, startDate);

        newSiege.setDefensiveStructures(generateDefaultMap());
        newSiege.setTargetStructures(generateDefaultMap());

        SiegeEntity savedSiege = siegeRepository.save(newSiege);

        auditLogService.logSystemAction(AuditAction.SIEGE_SYSTEM_EVENT, clanGroup.name(), "Created new Siege Cycle (PREP)");
        return savedSiege;
    }

    private List<SiegeStructure> generateDefaultMap() {
        List<SiegeStructure> map = new ArrayList<>();
        map.add(new SiegeStructure("Stronghold", SiegeStructureType.STRONGHOLD));
        for (int i = 1; i <= 2; i++) map.add(new SiegeStructure("Mana Shrine " + i, SiegeStructureType.SHRINE));
        for (int i = 1; i <= 4; i++) map.add(new SiegeStructure("Magic Tower " + i, SiegeStructureType.MAGIC_TOWER));
        for (int i = 1; i <= 5; i++) map.add(new SiegeStructure("Defense Tower " + i, SiegeStructureType.DEFENSE_TOWER));
        for (int i = 1; i <= 18; i++) map.add(new SiegeStructure("Post " + i, SiegeStructureType.POST));
        return map;
    }

    @Transactional
    public void assignDefenseTeam(String siegeId, String structureId, int slotNumber,
                                  String memberId, String leaderId, List<String> supportIds,
                                  Authentication authentication) {

        SiegeEntity siege = siegeRepository.findById(new ObjectId(siegeId))
                .orElseThrow(() -> new IllegalArgumentException("Siege not found"));

        SiegeStructure structure = siege.getDefensiveStructures().stream()
                .filter(s -> s.getId().equals(structureId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Structure not found"));

        SiegeStructure.SiegeSlot slot = structure.getSlots().stream()
                .filter(s -> s.getSlotNumber() == slotNumber)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Slot not found"));

        if (!StringUtils.hasText(memberId)) {
            String oldTargetName = slot.getPlayerName() != null ? slot.getPlayerName() : "Unknown Member";

            log.info("Clearing slot {} in structure {}", slotNumber, structure.getName());
            slot.setMemberId(null);
            slot.setPlayerName(null);
            slot.setLeaderChampionId(null);
            slot.setSupportChampionIds(new ArrayList<>());
            siegeRepository.save(siege);

            String details = String.format("Cleared Slot %d in %s", slotNumber, structure.getName());
            auditLogService.logAction(authentication, AuditAction.SIEGE_SLOT_UPDATE, oldTargetName, details);
            return;
        }

        // --- ASSIGNMENT LOGIC ---
        ClanmemberEntity member = clanmemberRepository.findById(new ObjectId(memberId))
                .orElseThrow(() -> new IllegalArgumentException("Member not found"));

        // Refactored to check OwnedChampion UUIDs
        List<OwnedChampion> rosterList = member.getRoster();
        Set<String> ownedInstanceIds = (rosterList != null) ?
                rosterList.stream().map(OwnedChampion::getId).collect(Collectors.toSet()) :
                Collections.emptySet();

        if (StringUtils.hasText(leaderId) && !ownedInstanceIds.contains(leaderId)) {
            throw new IllegalArgumentException("Member does not own this specific Leader champion instance.");
        }

        if (supportIds != null) {
            for (String supId : supportIds) {
                if (StringUtils.hasText(supId) && !ownedInstanceIds.contains(supId)) {
                    throw new IllegalArgumentException("Member does not own one or more support champion instances.");
                }
            }
        }

        if (!memberId.equals(slot.getMemberId())) {
            long usedSlots = countUsedSlots(siege, memberId);
            if (usedSlots >= member.getMaxDefenseScrolls()) {
                throw new IllegalArgumentException("Member has reached their Defense Scroll limit (" + member.getMaxDefenseScrolls() + ").");
            }
        }

        validateGlobalUniqueness(siege, memberId, leaderId, supportIds, structureId, slotNumber);

        slot.setMemberId(memberId);
        slot.setPlayerName(member.getIngameName());
        slot.setLeaderChampionId(leaderId);
        slot.setSupportChampionIds(supportIds != null ? supportIds : new ArrayList<>());

        siegeRepository.save(siege);

        String details = String.format("Assigned to Slot %d in %s", slotNumber, structure.getName());
        auditLogService.logAction(authentication, AuditAction.SIEGE_SLOT_UPDATE, member.getIngameName(), details);
    }

    public long countUsedSlots(SiegeEntity siege, String memberId) {
        return siege.getDefensiveStructures().stream()
                .flatMap(s -> s.getSlots().stream())
                .filter(slot -> memberId.equals(slot.getMemberId()))
                .count();
    }

    private void validateGlobalUniqueness(SiegeEntity siege, String memberId, String leaderId, List<String> supportIds, String currentStructId, int currentSlotNum) {
        Set<String> proposedChamps = new HashSet<>();
        if (StringUtils.hasText(leaderId)) proposedChamps.add(leaderId);
        if (supportIds != null) supportIds.stream().filter(StringUtils::hasText).forEach(proposedChamps::add);

        for (SiegeStructure struct : siege.getDefensiveStructures()) {
            for (SiegeStructure.SiegeSlot slot : struct.getSlots()) {
                if (memberId.equals(slot.getMemberId())) {
                    if (struct.getId().equals(currentStructId) && slot.getSlotNumber() == currentSlotNum) continue;

                    if (StringUtils.hasText(slot.getLeaderChampionId()) && proposedChamps.contains(slot.getLeaderChampionId())) {
                        throw new IllegalArgumentException("Champion Instance (ID: " + slot.getLeaderChampionId() + ") is already used in another slot.");
                    }
                    if (slot.getSupportChampionIds() != null) {
                        for (String sup : slot.getSupportChampionIds()) {
                            if (StringUtils.hasText(sup) && proposedChamps.contains(sup)) {
                                throw new IllegalArgumentException("Champion Instance (ID: " + sup + ") is already used in another slot.");
                            }
                        }
                    }
                }
            }
        }
    }
}