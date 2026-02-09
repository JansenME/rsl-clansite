package com.rsl.clansite.service;

import com.rsl.clansite.model.OwnedChampion;
import com.rsl.clansite.model.Team;
import com.rsl.clansite.model.entity.ChampionEntity;
import com.rsl.clansite.model.entity.ClanmemberEntity;
import com.rsl.clansite.model.enums.AuditAction;
import com.rsl.clansite.repository.ChampionRepository;
import com.rsl.clansite.repository.ClanmemberRepository;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class RosterSyncService {

    private final String uploadPath;
    private final ChampionRepository championRepository;
    private final ClanmemberRepository clanmemberRepository;
    private final AuditLogService auditLogService;

    public RosterSyncService(@Value("${app.upload.roster-path}") String uploadPath,
                             ChampionRepository championRepository,
                             ClanmemberRepository clanmemberRepository,
                             AuditLogService auditLogService) {
        this.uploadPath = uploadPath;
        this.championRepository = championRepository;
        this.clanmemberRepository = clanmemberRepository;
        this.auditLogService = auditLogService;
    }

    // --- DTOs ---

    @Data
    @Builder
    public static class ParsedChampion {
        private String masterId;
        private String name;
        private int rank;
        private int level;
        private int trueLevel;
    }

    @Data
    @Builder
    public static class MatchEdge {
        private OwnedChampion db;
        private ParsedChampion csv;
        private int diff; // Absolute difference in TrueLevel
    }

    @Data
    public static class SyncParseResult {
        private List<ParsedChampion> validEntries = new ArrayList<>();
        private List<String> unknownNames = new ArrayList<>();
    }

    @Data
    @Builder
    public static class ProposedChange {
        private String type;
        private String championName;
        private String masterId;
        private String instanceId;
        private String oldState;
        private String newState;
        private int newRank;
        private int newLevel;
        private List<String> affectedTeams;
    }

    @Data
    public static class SyncDiffResult {
        private List<ProposedChange> changes = new ArrayList<>();
        private List<String> unknownNames = new ArrayList<>();
        private int totalAdds = 0;
        private int totalUpdates = 0;
        private int totalRemoves = 0;
    }

    // --- Phase 3: The Reconciliation Engine ---

    public SyncDiffResult generateDiff(String discordId, ClanmemberEntity member) throws IOException {
        SyncParseResult parseResult = parseUpload(discordId);
        List<OwnedChampion> currentRoster = member.getRoster() != null ? member.getRoster() : new ArrayList<>();

        Map<String, List<OwnedChampion>> dbMap = currentRoster.stream()
                .collect(Collectors.groupingBy(OwnedChampion::getChampionId));

        Map<String, List<ParsedChampion>> csvMap = parseResult.getValidEntries().stream()
                .collect(Collectors.groupingBy(ParsedChampion::getMasterId));

        SyncDiffResult diffResult = new SyncDiffResult();
        diffResult.setUnknownNames(parseResult.getUnknownNames());

        Set<String> allMasterIds = new HashSet<>();
        allMasterIds.addAll(dbMap.keySet());
        allMasterIds.addAll(csvMap.keySet());

        for (String masterId : allMasterIds) {
            List<OwnedChampion> dbInstances = new ArrayList<>(dbMap.getOrDefault(masterId, new ArrayList<>()));
            List<ParsedChampion> csvInstances = new ArrayList<>(csvMap.getOrDefault(masterId, new ArrayList<>()));

            processChampionGroup(masterId, dbInstances, csvInstances, diffResult, member);
        }

        diffResult.getChanges().sort(Comparator.comparing(ProposedChange::getType).thenComparing(ProposedChange::getChampionName));
        return diffResult;
    }

    private void processChampionGroup(String masterId, List<OwnedChampion> dbInstances, List<ParsedChampion> csvInstances, SyncDiffResult diffResult, ClanmemberEntity member) {
        List<MatchEdge> allEdges = new ArrayList<>();

        // 1. Calculate score for EVERY combination
        for (OwnedChampion db : dbInstances) {
            int dbTrueLevel = calculateTrueLevel(db.getRank(), db.getLevel());
            for (ParsedChampion csv : csvInstances) {
                int diff = Math.abs(dbTrueLevel - csv.getTrueLevel());
                allEdges.add(MatchEdge.builder().db(db).csv(csv).diff(diff).build());
            }
        }

        allEdges.sort(Comparator.comparingInt(MatchEdge::getDiff));

        Set<String> matchedDbIds = new HashSet<>();
        Set<ParsedChampion> matchedCsv = Collections.newSetFromMap(new IdentityHashMap<>());

        // 3. Greedy Matching
        for (MatchEdge edge : allEdges) {
            if (!matchedDbIds.contains(edge.db.getId()) && !matchedCsv.contains(edge.csv)) {
                matchedDbIds.add(edge.db.getId());
                matchedCsv.add(edge.csv);

                if (edge.db.getRank() != edge.csv.getRank() || edge.db.getLevel() != edge.csv.getLevel()) {
                    diffResult.getChanges().add(ProposedChange.builder()
                            .type("UPDATE")
                            .championName(edge.csv.getName())
                            .masterId(masterId)
                            .instanceId(edge.db.getId())
                            .oldState(formatState(edge.db.getRank(), edge.db.getLevel()))
                            .newState(formatState(edge.csv.getRank(), edge.csv.getLevel()))
                            .newRank(edge.csv.getRank())
                            .newLevel(edge.csv.getLevel())
                            .build());
                    diffResult.setTotalUpdates(diffResult.getTotalUpdates() + 1);
                }
            }
        }

        // 4. Handle Removes
        for (OwnedChampion db : dbInstances) {
            if (!matchedDbIds.contains(db.getId())) {
                String name = resolveName(masterId);
                ProposedChange.ProposedChangeBuilder removeBuilder = ProposedChange.builder()
                        .type("REMOVE")
                        .championName(name)
                        .masterId(masterId)
                        .instanceId(db.getId())
                        .oldState(formatState(db.getRank(), db.getLevel()))
                        .newState("Deleted");

                List<String> affectedTeams = findAffectedTeams(member, db.getId());
                if (!affectedTeams.isEmpty()) {
                    removeBuilder.affectedTeams(affectedTeams);
                }

                diffResult.getChanges().add(removeBuilder.build());
                diffResult.setTotalRemoves(diffResult.getTotalRemoves() + 1);
            }
        }

        // 5. Handle Adds
        for (ParsedChampion csv : csvInstances) {
            if (!matchedCsv.contains(csv)) {
                diffResult.getChanges().add(ProposedChange.builder()
                        .type("ADD")
                        .championName(csv.getName())
                        .masterId(masterId)
                        .instanceId(null)
                        .oldState("-")
                        .newState(formatState(csv.getRank(), csv.getLevel()))
                        .newRank(csv.getRank())
                        .newLevel(csv.getLevel())
                        .build());
                diffResult.setTotalAdds(diffResult.getTotalAdds() + 1);
            }
        }
    }

    // --- Phase 5: Persistence (The Commit) ---

    @Transactional
    public void applySync(String discordId, ClanmemberEntity member, List<Integer> selectedIndices, Authentication authentication) throws IOException {
        SyncDiffResult diff = generateDiff(discordId, member);
        List<ProposedChange> allChanges = diff.getChanges();

        if (selectedIndices == null) selectedIndices = new ArrayList<>();
        if (member.getRoster() == null) member.setRoster(new ArrayList<>());

        int appliedAdds = 0;
        int appliedUpdates = 0;
        int appliedRemoves = 0;

        for (Integer index : selectedIndices) {
            if (index < 0 || index >= allChanges.size()) continue;
            ProposedChange change = allChanges.get(index);

            switch (change.getType()) {
                case "ADD" -> {
                    handleAddition(member, change);
                    appliedAdds++;
                }
                case "UPDATE" -> {
                    handleUpdate(member, change);
                    appliedUpdates++;
                }
                case "REMOVE" -> {
                    if (handleRemoval(member, change)) {
                        appliedRemoves++;
                    }
                }
            }
        }

        finalizeSync(member, discordId, authentication, appliedAdds, appliedUpdates, appliedRemoves);
    }

    private void handleAddition(ClanmemberEntity member, ProposedChange change) {
        member.getRoster().add(new OwnedChampion(
                change.getMasterId(),
                change.getNewLevel(),
                change.getNewRank()
        ));
    }

    private void handleUpdate(ClanmemberEntity member, ProposedChange change) {
        member.getRoster().stream()
                .filter(oc -> oc.getId().equals(change.getInstanceId()))
                .findFirst()
                .ifPresent(oc -> {
                    oc.setRank(change.getNewRank());
                    oc.setLevel(change.getNewLevel());
                });
    }

    /**
     * Removes the champion from the roster and detaches it from any teams.
     * @return true if a removal actually occurred
     */
    private boolean handleRemoval(ClanmemberEntity member, ProposedChange change) {
        boolean removed = member.getRoster().removeIf(oc -> oc.getId().equals(change.getInstanceId()));
        if (removed) {
            detachChampionFromTeams(member, change.getInstanceId());
        }
        return removed;
    }

    /**
     * THE FIX: Sets specific team slots to null instead of deleting the whole team.
     * Used for both CSV Sync and Manual Deletion.
     */
    private void detachChampionFromTeams(ClanmemberEntity member, String instanceId) {
        if (member.getKnownTeams() == null) return;

        for (Team team : member.getKnownTeams()) {
            if (Objects.equals(team.getLeaderChampionId(), instanceId)) team.setLeaderChampionId(null);
            if (Objects.equals(team.getChampion2Id(), instanceId)) team.setChampion2Id(null);
            if (Objects.equals(team.getChampion3Id(), instanceId)) team.setChampion3Id(null);
            if (Objects.equals(team.getChampion4Id(), instanceId)) team.setChampion4Id(null);
        }
    }

    private void finalizeSync(ClanmemberEntity member, String discordId, Authentication authentication, int adds, int updates, int removes) {
        member.setRosterLastUpdated(LocalDateTime.now());
        String actorName = "Unknown";
        if (authentication.getPrincipal() instanceof OAuth2User oauthUser) {
            String globalName = oauthUser.getAttribute("global_name");
            actorName = globalName != null ? globalName : authentication.getName();
        }
        member.setRosterUpdatedBy(actorName);

        clanmemberRepository.save(member);

        auditLogService.logAction(
                authentication,
                AuditAction.ROSTER_SYNC,
                member.getIngameName(),
                String.format("Sync Applied: +%d / ~%d / -%d", adds, updates, removes)
        );

        deleteSyncFile(discordId);
    }

    // --- Helpers ---
    private List<String> findAffectedTeams(ClanmemberEntity member, String instanceId) {
        if (member.getKnownTeams() == null) return Collections.emptyList();
        List<String> teamNames = new ArrayList<>();
        for (Team t : member.getKnownTeams()) {
            if (Objects.equals(t.getLeaderChampionId(), instanceId) ||
                    Objects.equals(t.getChampion2Id(), instanceId) ||
                    Objects.equals(t.getChampion3Id(), instanceId) ||
                    Objects.equals(t.getChampion4Id(), instanceId)) {
                teamNames.add(t.getTeamName());
            }
        }
        return teamNames;
    }

    private String resolveName(String masterId) {
        if (!org.bson.types.ObjectId.isValid(masterId)) return "Unknown (Invalid ID)";
        return championRepository.findById(new org.bson.types.ObjectId(masterId)).map(ChampionEntity::getName).orElse("Unknown Champion");
    }

    private String formatState(int rank, int level) { return rank + "★ Lvl " + level; }

    private String normalizeName(String name) {
        if (name == null) return "";
        return name.toLowerCase()
                .trim()
                .replace("’", "'")
                .replace("‘", "'")
                .replace("`", "'")
                .replace("´", "'");
    }

    // --- Parsing & Storage ---
    public SyncParseResult parseUpload(String discordId) throws IOException {
        File file = getExistingSyncFile(discordId);
        if (file == null) throw new IOException("No sync file found.");

        SyncParseResult result = new SyncParseResult();

        Map<String, ChampionEntity> masterMap = championRepository.findAll().stream()
                .collect(Collectors.toMap(
                        c -> normalizeName(c.getName()),
                        c -> c,
                        (c1, c2) -> c1
                ));

        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), "Windows-1252"))) {
            String line;
            boolean isFirstLine = true;

            while ((line = br.readLine()) != null) {
                line = line.replace("\uFEFF", ""); // BOM fix
                if (isFirstLine) { isFirstLine = false; continue; }
                if (!StringUtils.hasText(line)) continue;

                String[] cols = line.split(";");
                if (cols.length < 5) continue;

                String rawName = cols[2];
                ChampionEntity master = masterMap.get(normalizeName(rawName));

                if (master == null) {
                    if (StringUtils.hasText(rawName) && !result.getUnknownNames().contains(rawName)) {
                        result.getUnknownNames().add(rawName);
                    }
                    continue;
                }

                try {
                    int rank = Integer.parseInt(cols[3].trim());
                    int level = Integer.parseInt(cols[4].trim());
                    int trueLevel = calculateTrueLevel(rank, level);

                    result.getValidEntries().add(ParsedChampion.builder()
                            .masterId(master.getId().toHexString())
                            .name(master.getName())
                            .rank(rank)
                            .level(level)
                            .trueLevel(trueLevel)
                            .build());
                } catch (NumberFormatException e) { log.warn("Invalid number for {}: {}", rawName, e.getMessage()); }
            }
        }
        return result;
    }

    private int calculateTrueLevel(int rank, int level) { return ((rank - 1) * 5 * rank) + level; }

    public void saveUpload(MultipartFile file, String discordId) throws IOException {
        if (file.isEmpty()) throw new IllegalArgumentException("Empty file.");
        if (!StringUtils.hasText(discordId)) throw new IllegalArgumentException("No ID.");
        Path rootLocation = Paths.get(uploadPath);
        if (!Files.exists(rootLocation)) Files.createDirectories(rootLocation);
        Path dest = rootLocation.resolve(discordId + ".csv");
        try (var is = file.getInputStream()) { Files.copy(is, dest, StandardCopyOption.REPLACE_EXISTING); }
    }

    public File getExistingSyncFile(String discordId) {
        if (!StringUtils.hasText(discordId)) return null;
        Path p = Paths.get(uploadPath).resolve(discordId + ".csv");
        return (p.toFile().exists() && p.toFile().isFile()) ? p.toFile() : null;
    }

    public LocalDateTime getFileLastModified(String discordId) {
        File f = getExistingSyncFile(discordId);
        return f == null ? null : LocalDateTime.ofInstant(Instant.ofEpochMilli(f.lastModified()), ZoneId.systemDefault());
    }

    public void deleteSyncFile(String discordId) {
        try { Files.deleteIfExists(Paths.get(uploadPath).resolve(discordId + ".csv")); }
        catch (IOException e) { log.warn("Delete failed", e); }
    }
}