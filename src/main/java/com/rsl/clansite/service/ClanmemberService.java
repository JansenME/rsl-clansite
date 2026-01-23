package com.rsl.clansite.service;

import com.rsl.clansite.client.DiscordApiClient;
import com.rsl.clansite.exceptions.UnlinkedAccountException;
import com.rsl.clansite.model.ClanmemberViewData;
import com.rsl.clansite.model.Team;
import com.rsl.clansite.model.dto.MemberLookupResult;
import com.rsl.clansite.model.dto.NewClanmemberDTO;
import com.rsl.clansite.model.dto.SyncStatusDTO;
import com.rsl.clansite.model.entity.ChampionEntity;
import com.rsl.clansite.model.entity.ClanmemberEntity;
import com.rsl.clansite.model.entity.SiegeConditionEntity;
import com.rsl.clansite.model.entity.VisitorLogEntity;
import com.rsl.clansite.model.enums.AuditAction;
import com.rsl.clansite.model.enums.ClanGroup;
import com.rsl.clansite.model.enums.ClanRank;
import com.rsl.clansite.model.enums.MemberStatus;
import com.rsl.clansite.repository.ChampionRepository;
import com.rsl.clansite.repository.ClanmemberRepository;
import com.rsl.clansite.repository.VisitorLogRepository;
import com.rsl.clansite.security.SecurityConfig;
import com.rsl.clansite.security.SecurityService;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ClanmemberService {
    private final ClanmemberRepository clanmemberRepository;
    private final VisitorLogRepository visitorLogRepository;
    private final DiscordRoleService discordRoleService;
    private final AuditLogService auditLogService;
    private final DiscordApiClient discordApiClient;
    private final SiteAssetService siteAssetService;
    private final ChampionRepository championRepository;
    private final SiegeConditionService siegeConditionService;
    private final SecurityService securityService;

    @Autowired
    public ClanmemberService(final ClanmemberRepository clanmemberRepository,
                             final VisitorLogRepository visitorLogRepository,
                             final DiscordRoleService discordRoleService,
                             final AuditLogService auditLogService,
                             final DiscordApiClient discordApiClient,
                             final SiteAssetService siteAssetService,
                             final ChampionRepository championRepository,
                             final SiegeConditionService siegeConditionService,
                             final SecurityService securityService) {
        this.clanmemberRepository = clanmemberRepository;
        this.visitorLogRepository = visitorLogRepository;
        this.discordRoleService = discordRoleService;
        this.auditLogService = auditLogService;
        this.discordApiClient = discordApiClient;
        this.siteAssetService = siteAssetService;
        this.championRepository = championRepository;
        this.siegeConditionService = siegeConditionService;
        this.securityService = securityService;
    }

    public record AccountDetailDTO(String ingameName, ClanRank clanRank, ClanGroup clanGroup) {}

    public record LoginHistoryDTO(
            String discordId,
            String discordName,
            String avatarUrl,
            LocalDateTime lastLogin,
            String lastLocation,
            List<AccountDetailDTO> accounts
    ) {}

    @Transactional
    public void deleteKnownTeam(HttpSession session, Authentication authentication, String teamId, String targetMemberId) {
        ClanmemberEntity targetMember;

        if (StringUtils.hasText(targetMemberId)) {
            targetMember = getMemberById(targetMemberId);

            if (!isOwnProfile(targetMember, authentication) && !securityService.isCoordinator(authentication)) {
                throw new AccessDeniedException("You do not have permission to delete teams for other members.");
            }
        } else {
            targetMember = getActiveClanmember(session, authentication);
        }

        if (targetMember == null) {
            throw new UnlinkedAccountException("No active profile found.");
        }

        if (targetMember.getKnownTeams() != null) {
            boolean removed = targetMember.getKnownTeams().removeIf(t -> t.getId().equals(teamId));

            if (removed) {
                clanmemberRepository.save(targetMember);
                log.info("Deleted Known Team {} for member {}", teamId, targetMember.getIngameName());
            } else {
                throw new IllegalArgumentException("Team not found in profile.");
            }
        }
    }

    public boolean isOwnProfile(ClanmemberEntity member, Authentication authentication) {
        if (member == null || authentication == null) return false;
        String currentDiscordId = authentication.getName();
        return currentDiscordId.equals(member.getDiscordId());
    }

    public List<LoginHistoryDTO> getDeduplicatedLoginHistory() {
        List<ClanmemberEntity> allLogins = clanmemberRepository.findAll().stream()
                .filter(m -> m.getLastLogin() != null)
                .toList();

        Map<String, List<ClanmemberEntity>> groupedByDiscord = allLogins.stream()
                .filter(m -> StringUtils.hasText(m.getDiscordId()))
                .collect(Collectors.groupingBy(ClanmemberEntity::getDiscordId));

        List<LoginHistoryDTO> historyList = new ArrayList<>();

        for (Map.Entry<String, List<ClanmemberEntity>> entry : groupedByDiscord.entrySet()) {
            String discordId = entry.getKey();
            List<ClanmemberEntity> allAccounts = entry.getValue();

            boolean hasActiveAccount = allAccounts.stream()
                    .anyMatch(m -> m.getStatus() == MemberStatus.ACTIVE);

            if (!hasActiveAccount) {
                continue;
            }

            ClanmemberEntity latestActivityMember = allAccounts.stream()
                    .filter(m -> m.getLastLogin() != null)
                    .max((m1, m2) -> m1.getLastLogin().compareTo(m2.getLastLogin()))
                    .orElse(allAccounts.get(0));

            LocalDateTime latestLogin = latestActivityMember.getLastLogin();
            if (latestLogin == null) continue;

            List<AccountDetailDTO> activeAccountDetails = allAccounts.stream()
                    .filter(m -> m.getStatus() == MemberStatus.ACTIVE)
                    .sorted(this::compareAccountsForHistory)
                    .map(m -> new AccountDetailDTO(m.getIngameName(), m.getClanRank(), m.getClanGroup()))
                    .toList();

            ClanmemberEntity primary = allAccounts.get(0);
            String avatarUrl = buildAvatarUrl(discordId, primary.getAvatarHash());

            historyList.add(new LoginHistoryDTO(
                    discordId,
                    primary.getDiscordName(),
                    avatarUrl,
                    latestLogin,
                    latestActivityMember.getLastLocation(),
                    activeAccountDetails
            ));
        }

        historyList.sort((a, b) -> b.lastLogin().compareTo(a.lastLogin()));

        return historyList;
    }

    private int compareAccountsForHistory(ClanmemberEntity m1, ClanmemberEntity m2) {
        if (m1.getClanGroup() != m2.getClanGroup()) {
            if (m1.getClanGroup() == null) return 1;
            if (m2.getClanGroup() == null) return -1;
            return m1.getClanGroup().compareTo(m2.getClanGroup());
        }

        int rankComparison = compareClanRanks(m1, m2);
        if (rankComparison != 0) {
            return rankComparison;
        }

        String n1 = m1.getIngameName() != null ? m1.getIngameName() : "";
        String n2 = m2.getIngameName() != null ? m2.getIngameName() : "";
        return n1.compareToIgnoreCase(n2);
    }

    public ClanmemberEntity getActiveClanmember(HttpSession session, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        String discordId = ((OAuth2User) authentication.getPrincipal()).getAttribute("id");
        if (discordId == null) return null;

        List<ClanmemberEntity> linkedMembers = getLinkedClanmembers(discordId);
        if (linkedMembers.isEmpty()) return null;

        String activeId = (String) session.getAttribute("ACTIVE_MEMBER_ID");

        if (activeId != null) {
            Optional<ClanmemberEntity> activeEntity = linkedMembers.stream()
                    .filter(m -> m.getId().toHexString().equals(activeId))
                    .findFirst();

            if (activeEntity.isPresent()) {
                return activeEntity.get();
            }
        }
        return linkedMembers.stream()
                .filter(m -> m.getStatus() == MemberStatus.ACTIVE)
                .findFirst()
                .orElse(linkedMembers.get(0));
    }

    public String manageActiveMemberSession(HttpSession session, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        String currentDiscordId = authentication.getName();
        List<ClanmemberEntity> linkedMembers = getLinkedClanmembers(currentDiscordId);
        String activeMemberId = (String) session.getAttribute("ACTIVE_MEMBER_ID");

        if (activeMemberId == null && !linkedMembers.isEmpty()) {
            ClanmemberEntity defaultMember = linkedMembers.stream()
                    .filter(m -> m.getStatus() == MemberStatus.ACTIVE)
                    .findFirst()
                    .orElse(linkedMembers.get(0));

            activeMemberId = defaultMember.getId().toHexString();
            session.setAttribute("ACTIVE_MEMBER_ID", activeMemberId);
        }
        return activeMemberId;
    }

    public boolean switchActiveMember(HttpSession session, Authentication authentication, String newMemberId) {
        if (authentication == null) return false;

        String currentDiscordId = authentication.getName();
        List<ClanmemberEntity> ownedAccounts = getLinkedClanmembers(currentDiscordId);

        boolean isOwned = ownedAccounts.stream()
                .anyMatch(member -> member.getId().toHexString().equals(newMemberId));

        if (isOwned) {
            session.setAttribute("ACTIVE_MEMBER_ID", newMemberId);
            return true;
        }
        return false;
    }

    public MemberLookupResult performMemberLookup(String discordId) {
        try {
            NewClanmemberDTO dto = lookupDiscordUser(discordId);

            StringBuilder warningMsg = new StringBuilder();

            List<String> roles = dto.getDiscordRoles();
            if (roles != null &&
                    roles.contains(discordRoleService.getT1RoleId()) &&
                    roles.contains(discordRoleService.getT2RoleId())) {
                warningMsg.append("Notice: This user has both T1 and T2 roles in Discord. Please manually select the correct Clan Group below. ");
            }

            if (isDiscordIdInRoster(discordId)) {
                warningMsg.append("Notice: This Discord ID is already in the roster. You can still add this as an alt account.");
            }

            return MemberLookupResult.success(dto, warningMsg.toString().trim());

        } catch (Exception e) {
            return MemberLookupResult.failure("Error looking up user: " + e.getMessage());
        }
    }

    @Scheduled(cron = "0 */5 * * * *")
    @Transactional
    public void updateAllClanmemberDiscordRoles() {
        siteAssetService.syncFavicon();

        List<NewClanmemberDTO> discordMembers = discordApiClient.getAllGuildMembers();

        if (discordMembers.isEmpty()) {
            log.warn("Scheduled Sync: Received empty member list from Discord. Aborting sync to prevent data loss.");
            return;
        }

        Map<String, NewClanmemberDTO> discordDataMap = discordMembers.stream()
                .filter(d -> d.getDiscordId() != null)
                .collect(Collectors.toMap(NewClanmemberDTO::getDiscordId, Function.identity(), (a, b) -> a));

        List<ClanmemberEntity> allLinkedMembers = clanmemberRepository.findAllByDiscordIdIsNotNull().stream()
                .filter(m -> m.getStatus() == MemberStatus.ACTIVE)
                .toList();

        Map<String, List<ClanmemberEntity>> membersByDiscordId = allLinkedMembers.stream()
                .collect(Collectors.groupingBy(ClanmemberEntity::getDiscordId));

        int membersUpdated = 0;

        for (Map.Entry<String, List<ClanmemberEntity>> entry : membersByDiscordId.entrySet()) {
            String discordId = entry.getKey();
            try {
                List<ClanmemberEntity> userAccounts = entry.getValue();

                if (userAccounts.size() > 1) {
                    log.info("Scheduled Sync: Safety Lock triggered for Discord ID {}. Multiple accounts ({}) found, skipping auto-sync.",
                            discordId, userAccounts.size());
                    continue;
                }

                NewClanmemberDTO discordData = discordDataMap.get(discordId);
                if (discordData != null) {
                    ClanmemberEntity member = userAccounts.get(0);
                    if (applyDiscordDataToMember(member, discordData, false)) {
                        membersUpdated++;
                    }
                } else {
                    log.info("Scheduled Sync: User ID {} not found in current Discord member list.", discordId);
                }
            } catch (Exception e) {
                log.error("Scheduled Sync: Fatal error processing Discord ID {}: {}", discordId, e.getMessage());
            }
        }

        if (membersUpdated > 0) {
            log.info("Scheduled Sync: Updated {} members using Batch API strategy.", membersUpdated);
        }
    }

    private boolean applyDiscordDataToMember(ClanmemberEntity member, NewClanmemberDTO discordData, boolean isMultiAccount) {
        try {
            List<String> sortedRoles = discordRoleService.sortRoles(discordData.getDiscordRoles());
            String newAvatarHash = discordData.getAvatarHash();
            ClanGroup newDetectedGroup = resolveClanGroup(sortedRoles);

            boolean rolesChanged = !sortedRoles.equals(member.getDiscordRoles());
            boolean avatarChanged = member.getAvatarHash() == null || !newAvatarHash.equals(member.getAvatarHash());
            boolean groupChanged = false;

            boolean discordNameChanged = !Objects.equals(member.getDiscordName(), discordData.getDiscordName());
            boolean nicknameChanged = !Objects.equals(member.getPlayerNickname(), discordData.getPlayerNickname());

            if (!isMultiAccount && newDetectedGroup != null && !newDetectedGroup.equals(member.getClanGroup())) {
                log.info("SYNC: Clan Group change detected for '{}': {} -> {}",
                        member.getIngameName(), member.getClanGroup(), newDetectedGroup);

                member.setClanGroup(newDetectedGroup);
                groupChanged = true;
            }

            if (rolesChanged) member.setDiscordRoles(sortedRoles);
            if (avatarChanged) member.setAvatarHash(newAvatarHash);

            if (discordNameChanged) member.setDiscordName(discordData.getDiscordName());
            if (nicknameChanged) member.setPlayerNickname(discordData.getPlayerNickname());

            if (rolesChanged || avatarChanged || groupChanged || discordNameChanged || nicknameChanged) {
                clanmemberRepository.save(member);
                return true;
            }
        } catch (Exception e) {
            log.error("SYNC: Error updating member {}: {}", member.getIngameName(), e.getMessage());
        }
        return false;
    }

    public void linkClanmember(final String discordId, final String globalName, final String avatarHash, final List<String> currentDiscordRoles) {
        List<ClanmemberEntity> linkedMembers = clanmemberRepository.findAllByDiscordId(discordId);

        if (linkedMembers.isEmpty()) {
            boolean hasT1 = currentDiscordRoles.contains(discordRoleService.getT1RoleId());
            boolean hasT2 = currentDiscordRoles.contains(discordRoleService.getT2RoleId());

            if (hasT1 || hasT2) {
                log.info("Auto-Onboarding: User {} has Clan Roles but no profile. Creating Skeleton Entity.", globalName);

                ClanmemberEntity skeleton = new ClanmemberEntity();
                skeleton.setDiscordId(discordId);
                skeleton.setDiscordName(globalName);
                skeleton.setAvatarHash(avatarHash);
                skeleton.setDiscordRoles(currentDiscordRoles);

                skeleton.setIngameName(globalName);
                skeleton.setPlayerNickname(globalName);

                skeleton.setClanRank(null);

                skeleton.setStatus(MemberStatus.ACTIVE);
                skeleton.setLastLogin(LocalDateTime.now());

                if (hasT1 && !hasT2) skeleton.setClanGroup(ClanGroup.T1);
                else if (hasT2 && !hasT1) skeleton.setClanGroup(ClanGroup.T2);

                clanmemberRepository.save(skeleton);
                return;
            }

            updateVisitorLog(discordId, globalName, avatarHash);
            return;
        }

        List<String> sortedRoles = discordRoleService.sortRoles(currentDiscordRoles);
        ClanGroup detectedGroup = resolveClanGroup(currentDiscordRoles);

        for (ClanmemberEntity member : linkedMembers) {
            member.setLastLogin(LocalDateTime.now());
            updateSingleLinkedMember(member, globalName, avatarHash, sortedRoles, detectedGroup);
        }
    }

    public List<ClanmemberEntity> getLinkedClanmembers(final String discordId) {
        if (discordId == null) return List.of();

        return clanmemberRepository.findAllByDiscordId(discordId);
    }

    public List<ClanmemberEntity> findAllClanmemberEntities() {
        List<ClanmemberEntity> members = clanmemberRepository.findAll().stream()
                .filter(m -> m.getStatus() == MemberStatus.ACTIVE && m.getClanRank() != null)
                .collect(Collectors.toList());

        members.sort(this::compareClanRanks);
        return members;
    }

    public List<ClanmemberEntity> findPendingClanmembers() {
        return clanmemberRepository.findAll().stream()
                .filter(m -> m.getStatus() == MemberStatus.ACTIVE && m.getClanRank() == null)
                .collect(Collectors.toList());
    }

    public List<ClanmemberEntity> findInactiveClanmemberEntities() {
        List<ClanmemberEntity> members = clanmemberRepository.findAll().stream()
                .filter(m -> m.getStatus() == MemberStatus.INACTIVE)
                .collect(Collectors.toList());

        members.sort((m1, m2) -> {
            LocalDateTime d1 = m1.getStatusChangedDate();
            LocalDateTime d2 = m2.getStatusChangedDate();
            if (d1 == null && d2 == null) return 0;
            if (d1 == null) return 1;
            if (d2 == null) return -1;
            return d2.compareTo(d1);
        });
        return members;
    }

    public ClanmemberViewData getUserViewData(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof OAuth2User oauth2User)) {
            return new ClanmemberViewData(null, List.of(), null);
        }

        String discordId = oauth2User.getAttribute("id");
        String globalName = oauth2User.getAttribute("global_name");
        String discordUserName = (globalName != null) ? globalName : "Unknown User";
        String avatarHash = oauth2User.getAttribute("avatar");

        return new ClanmemberViewData(
                discordUserName,
                resolveRoleNamesForUser(discordId),
                buildAvatarUrl(discordId, avatarHash)
        );
    }

    public void saveNewClanmember(NewClanmemberDTO dto, Authentication authentication) {
        ClanmemberEntity newMember = new ClanmemberEntity();

        newMember.setDiscordId(dto.getDiscordId());
        newMember.setDiscordName(dto.getDiscordName());
        newMember.setPlayerNickname(dto.getPlayerNickname());
        newMember.setIngameName(dto.getIngameName());

        newMember.setClanRank(dto.getClanRank() != null ? dto.getClanRank() : ClanRank.SOLDIER);

        newMember.setClanGroup(dto.getClanGroup());
        newMember.setAvatarHash(dto.getAvatarHash());
        newMember.setDiscordRoles(dto.getDiscordRoles() != null ? dto.getDiscordRoles() : List.of());
        newMember.setRosterChampionIds(new ArrayList<>());

        if (dto.getDiscordId() != null) {
            Optional<VisitorLogEntity> visitorOpt = visitorLogRepository.findByDiscordId(dto.getDiscordId());
            if (visitorOpt.isPresent()) {
                newMember.setLastLogin(visitorOpt.get().getLastLogin());
                visitorLogRepository.delete(visitorOpt.get());
                log.info("Migrated Visitor Log to new Member profile for: {}", dto.getIngameName());
            }
        }

        clanmemberRepository.save(newMember);

        auditLogService.logAction(
                authentication,
                AuditAction.MEMBER_ADD,
                dto.getIngameName(),
                "Manually added to Roster: " + dto.getIngameName()
        );
    }

    public NewClanmemberDTO lookupDiscordUser(String userId) throws RuntimeException {
        Optional<NewClanmemberDTO> result = discordApiClient.getDiscordMember(userId);

        if (result.isEmpty()) {
            throw new RuntimeException("Discord User ID not found in the clan server.");
        }

        NewClanmemberDTO dto = result.get();
        dto.setClanGroup(resolveClanGroup(dto.getDiscordRoles()));

        return dto;
    }

    public boolean isDiscordIdInRoster(String discordId) {
        return clanmemberRepository.countByDiscordId(discordId) > 0;
    }

    public boolean isPlayerIngameNameInUse(String ingameName) {
        return clanmemberRepository.existsByIngameName(ingameName);
    }

    public void deleteById(String id, HttpSession session, Authentication authentication) {
        Optional<ClanmemberEntity> memberOpt = clanmemberRepository.findById(new ObjectId(id));
        if (memberOpt.isEmpty()) return;

        ClanmemberEntity member = memberOpt.get();
        String targetName = member.getIngameName();

        member.setStatus(MemberStatus.INACTIVE);
        member.setStatusChangedDate(LocalDateTime.now());
        clanmemberRepository.save(member);

        auditLogService.logAction(
                authentication,
                AuditAction.MEMBER_DELETE,
                targetName,
                "Member Deactivated (Soft Delete)"
        );

        if (session != null) {
            String activeId = (String) session.getAttribute("ACTIVE_MEMBER_ID");
            if (id.equals(activeId)) {
                session.removeAttribute("ACTIVE_MEMBER_ID");
            }
        }
    }

    public void reactivateMember(String id, Authentication authentication) {
        Optional<ClanmemberEntity> memberOpt = clanmemberRepository.findById(new ObjectId(id));
        if (memberOpt.isEmpty()) return;

        ClanmemberEntity member = memberOpt.get();
        member.setStatus(MemberStatus.ACTIVE);
        member.setStatusChangedDate(LocalDateTime.now());

        if (member.getDiscordId() != null) {
            try {
                boolean isMultiAccount = clanmemberRepository.findAllByDiscordId(member.getDiscordId()).size() > 1;
                tryUpdateMemberRoles(member, isMultiAccount);
            } catch (Exception e) {
                log.warn("Auto-sync failed during reactivation for {}", member.getIngameName());
            }
        }

        clanmemberRepository.save(member);

        auditLogService.logAction(
                authentication,
                AuditAction.MEMBER_REACTIVATE,
                member.getIngameName(),
                "Member Re-activated from Archive"
        );
    }

    public ClanmemberEntity getMemberById(String id) {
        if (id == null || !ObjectId.isValid(id)) {
            throw new IllegalArgumentException("Invalid Member ID provided");
        }
        return clanmemberRepository.findById(new ObjectId(id))
                .orElseThrow(() -> new UnlinkedAccountException("Member not found with ID: " + id));
    }

    public ClanmemberViewData getViewDataForMember(ClanmemberEntity member) {
        String discordUserName = member.getDiscordName() != null ? member.getDiscordName() : "Unknown";

        List<String> roleNames = List.of();
        if (member.getDiscordRoles() != null) {
            roleNames = member.getDiscordRoles().stream()
                    .map(discordRoleService::getRoleName)
                    .toList();
        }

        String avatarUrl = buildAvatarUrl(member.getDiscordId(), member.getAvatarHash());

        return new ClanmemberViewData(discordUserName, roleNames, avatarUrl);
    }

    public NewClanmemberDTO mapEntityToDto(ClanmemberEntity entity) {
        NewClanmemberDTO dto = new NewClanmemberDTO();
        dto.setDiscordId(entity.getDiscordId());
        dto.setDiscordName(entity.getDiscordName());
        dto.setPlayerNickname(entity.getPlayerNickname());
        dto.setIngameName(entity.getIngameName());

        dto.setClanRank(entity.getClanRank());

        dto.setClanGroup(entity.getClanGroup());
        dto.setAvatarHash(entity.getAvatarHash());
        dto.setDiscordRoles(entity.getDiscordRoles());
        return dto;
    }

    public void updateClanmember(String id, NewClanmemberDTO dto, Authentication authentication) {
        ClanmemberEntity member = getMemberById(id);

        Optional<ClanmemberEntity> existingWithSameName = clanmemberRepository.findByIngameName(dto.getIngameName());
        if (existingWithSameName.isPresent() && !existingWithSameName.get().getId().toHexString().equals(id)) {
            throw new IllegalArgumentException("The In-Game Name '" + dto.getIngameName() + "' is already in use by another member.");
        }

        String oldDiscordId = member.getDiscordId();
        String newDiscordId = dto.getDiscordId();

        if (!StringUtils.hasText(newDiscordId)) {
            newDiscordId = null;
        }

        boolean discordIdChanged = !Objects.equals(oldDiscordId, newDiscordId);

        member.setIngameName(dto.getIngameName());

        member.setClanRank(dto.getClanRank());

        member.setClanGroup(dto.getClanGroup());
        member.setDiscordId(newDiscordId);

        clanmemberRepository.save(member);

        auditLogService.logAction(
                authentication,
                AuditAction.MEMBER_UPDATE,
                member.getIngameName(),
                "Updated details for: " + member.getIngameName()
        );

        if (discordIdChanged && newDiscordId != null) {
            try {
                List<ClanmemberEntity> allLinked = clanmemberRepository.findAllByDiscordId(newDiscordId);
                boolean isMultiAccount = allLinked.size() > 1;

                tryUpdateMemberRoles(member, isMultiAccount);
                log.info("Auto-synced member {} after manual Discord ID link.", member.getIngameName());

            } catch (Exception e) {
                log.warn("Auto-sync failed after linking Discord ID for {}: {}", member.getIngameName(), e.getMessage());
            }
        }
    }

    public List<SyncStatusDTO> getMemberSyncStatus() {
        List<ClanmemberEntity> members = clanmemberRepository.findAll().stream()
                .filter(m -> m.getStatus() == MemberStatus.ACTIVE)
                .toList();

        List<SyncStatusDTO> statusList = new ArrayList<>();

        for (ClanmemberEntity member : members) {
            SyncStatusDTO status = new SyncStatusDTO();
            status.setMemberId(member.getId().toHexString());
            status.setDiscordId(member.getDiscordId());
            status.setIngameName(member.getIngameName());

            if (!StringUtils.hasText(member.getDiscordId())) {
                status.setStatusMessage("No Discord ID linked");
                status.setNicknameSynced(false);
                status.setRolesSynced(false);
                status.setAvatarSynced(false);
                status.setDiscordName("NOT FOUND");
                statusList.add(status);
                continue;
            }

            try {
                Optional<NewClanmemberDTO> apiResult = discordApiClient.getDiscordMember(member.getDiscordId());

                if (apiResult.isEmpty()) {
                    status.setStatusMessage("User not found in Discord Server");
                    status.setNicknameSynced(false);
                    status.setRolesSynced(false);
                    status.setAvatarSynced(false);
                    status.setDiscordName("NOT FOUND");
                } else {
                    NewClanmemberDTO liveData = apiResult.get();

                    String liveNick = liveData.getPlayerNickname();
                    String liveGlobal = liveData.getDiscordName();

                    String displayName = "Unknown";

                    if (isValidName(liveNick)) {
                        displayName = liveNick;
                    } else if (isValidName(liveGlobal)) {
                        displayName = liveGlobal;
                    }

                    status.setDiscordName(displayName);

                    boolean avatarMatch = java.util.Objects.equals(member.getAvatarHash(), liveData.getAvatarHash());
                    status.setAvatarSynced(avatarMatch);

                    List<String> liveRoles = discordRoleService.sortRoles(liveData.getDiscordRoles());
                    List<String> dbRoles = discordRoleService.sortRoles(member.getDiscordRoles());
                    status.setRolesSynced(liveRoles.equals(dbRoles));

                    String dbNick = member.getPlayerNickname();
                    status.setNicknameSynced(java.util.Objects.equals(dbNick, liveNick));
                }

            } catch (Exception e) {
                status.setStatusMessage("API Error: " + e.getMessage());
                status.setDiscordName("ERROR");
                status.setNicknameSynced(false);
                status.setRolesSynced(false);
                status.setAvatarSynced(false);
            }

            statusList.add(status);
        }

        statusList.sort((a, b) -> {
            int scoreA = getSortScore(a);
            int scoreB = getSortScore(b);

            if (scoreA != scoreB) {
                return Integer.compare(scoreA, scoreB);
            }
            return a.getIngameName().compareToIgnoreCase(b.getIngameName());
        });

        return statusList;
    }

    public void syncSingleMember(String id, Authentication authentication) {
        ClanmemberEntity member = getMemberById(id);

        if (!StringUtils.hasText(member.getDiscordId())) {
            throw new IllegalArgumentException("Cannot sync: This member is not linked to a Discord ID.");
        }

        List<ClanmemberEntity> allLinked = clanmemberRepository.findAllByDiscordId(member.getDiscordId());
        boolean isMultiAccount = allLinked.size() > 1;

        boolean updated = tryUpdateMemberRoles(member, isMultiAccount);

        if (updated) {
            auditLogService.logAction(
                    authentication,
                    AuditAction.MEMBER_UPDATE,
                    member.getIngameName(),
                    "Manual Data Sync: Updated Discord Roles/Avatar/Nickname."
            );
        }
    }

    @Transactional
    public void updateLastSeen(String discordId, String location) {
        if (!StringUtils.hasText(discordId)) return;

        List<ClanmemberEntity> members = clanmemberRepository.findAllByDiscordId(discordId);
        if (!members.isEmpty()) {
            for (ClanmemberEntity member : members) {
                if (shouldUpdateTimestamp(member.getLastLogin())) {
                    member.setLastLogin(LocalDateTime.now());
                    member.setLastLocation(location);
                    clanmemberRepository.save(member);
                }
            }
            return;
        }

        Optional<VisitorLogEntity> visitorOpt = visitorLogRepository.findByDiscordId(discordId);
        if (visitorOpt.isPresent()) {
            VisitorLogEntity visitor = visitorOpt.get();
            if (shouldUpdateTimestamp(visitor.getLastLogin())) {
                visitor.updateLogin();
                visitor.setLastLocation(location);
                visitorLogRepository.save(visitor);
            }
        }
    }

    public Optional<Set<SimpleGrantedAuthority>> getFreshAuthorities(String discordId) {
        return clanmemberRepository.findAllByDiscordId(discordId).stream()
                .findFirst()
                .map(member -> {
                    Set<String> dbRoles = new HashSet<>(member.getDiscordRoles());
                    return discordRoleService.getAuthoritiesForRoles(dbRoles, discordId);
                });
    }

    @Transactional
    public void saveKnownTeam(HttpSession session, Authentication authentication, Team team, String targetMemberId) {
        ClanmemberEntity activeUser = getActiveClanmember(session, authentication);
        ClanmemberEntity targetMember;

        if (StringUtils.hasText(targetMemberId)) {
            targetMember = getMemberById(targetMemberId);

            if (!isOwnProfile(targetMember, authentication) && !securityService.isCoordinator(authentication)) {
                throw new AccessDeniedException("You do not have permission to manage teams for other members.");
            }

        } else {
            targetMember = activeUser;
        }

        if (targetMember == null) {
            throw new UnlinkedAccountException("No active profile session found.");
        }

        if (team.getId() == null) {
            team.setId(new ObjectId().toHexString());
        }

        if (!StringUtils.hasText(team.getTeamName())) {
            throw new IllegalArgumentException("Team Name is required.");
        }

        if (team.getSiegeConditionId() != null) {
            validateTeamAgainstCondition(team);
        }

        if (targetMember.getKnownTeams() == null) {
            targetMember.setKnownTeams(new ArrayList<>());
        }

        boolean replaced = false;
        for (int i = 0; i < targetMember.getKnownTeams().size(); i++) {
            if (targetMember.getKnownTeams().get(i).getId().equals(team.getId())) {
                targetMember.getKnownTeams().set(i, team);
                replaced = true;
                break;
            }
        }

        if (!replaced) {
            targetMember.getKnownTeams().add(team);
        }

        clanmemberRepository.save(targetMember);

        String actorName = authentication.getName();

        auditLogService.logAction(
                authentication,
                AuditAction.MEMBER_UPDATE,
                targetMember.getIngameName(),
                "Saved Known Team: " + team.getTeamName() + " for " + targetMember.getIngameName()
        );
        log.info("Saved Known Team '{}' for member {} (Actor: {})",
                team.getTeamName(), targetMember.getIngameName(), actorName);
    }

    private boolean isValidName(String s) {
        return StringUtils.hasText(s) && !"null".equalsIgnoreCase(s);
    }

    private void validateTeamAgainstCondition(Team team) {
        SiegeConditionEntity condition = siegeConditionService.getConditionById(team.getSiegeConditionId());

        List<String> championIds = new ArrayList<>();
        if (team.getLeaderChampionId() != null) championIds.add(team.getLeaderChampionId());
        if (team.getChampion2Id() != null) championIds.add(team.getChampion2Id());
        if (team.getChampion3Id() != null) championIds.add(team.getChampion3Id());
        if (team.getChampion4Id() != null) championIds.add(team.getChampion4Id());

        if (championIds.isEmpty()) return;

        List<ObjectId> objectIds = championIds.stream()
                .filter(ObjectId::isValid)
                .map(ObjectId::new)
                .collect(Collectors.toList());

        List<ChampionEntity> champions = championRepository.findAllById(objectIds);

        for (ChampionEntity champ : champions) {
            boolean isValid = checkChampionCondition(champ, condition);
            if (!isValid) {
                throw new IllegalArgumentException("Validation Failed: Champion " + champ.getName() + " does not meet the Siege Condition: " + condition.getCategory() + " = " + condition.getConditionKey());
            }
        }
    }

    private boolean checkChampionCondition(ChampionEntity champ, SiegeConditionEntity condition) {
        try {
            String getterName = "get" + capitalize(condition.getCategory().name());
            Method method = ChampionEntity.class.getMethod(getterName);

            Object value = method.invoke(champ);

            if (value instanceof Enum<?>) {
                String enumName = ((Enum<?>) value).name();
                return enumName.equals(condition.getConditionKey());
            }
            return false;
        } catch (Exception e) {
            log.error("Reflection error during validation", e);
            return false;
        }
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }

    private boolean shouldUpdateTimestamp(LocalDateTime lastDate) {
        if (lastDate == null) return true;
        return lastDate.isBefore(LocalDateTime.now().minusMinutes(1));
    }

    private int getSortScore(com.rsl.clansite.model.dto.SyncStatusDTO dto) {
        boolean hasId = StringUtils.hasText(dto.getDiscordId());

        if (!hasId) {
            return 3;
        }

        boolean fullySynced = dto.isAvatarSynced() && dto.isRolesSynced() && dto.isNicknameSynced();

        if (!fullySynced) {
            return 1;
        }

        return 2;
    }

    private void updateVisitorLog(String discordId, String username, String avatarHash) {
        VisitorLogEntity visitor = visitorLogRepository.findByDiscordId(discordId)
                .orElse(new VisitorLogEntity(discordId, username, avatarHash));

        if (visitor.getId() != null) {
            visitor.setUsername(username);
            visitor.setAvatarHash(avatarHash);
            visitor.updateLogin();
        }

        visitorLogRepository.save(visitor);
    }

    private boolean tryUpdateMemberRoles(ClanmemberEntity member, boolean isMultiAccount) {
        try {
            if (!StringUtils.hasText(member.getDiscordId())) {
                return false;
            }

            Optional<NewClanmemberDTO> discordDataOpt = discordApiClient.getDiscordMember(member.getDiscordId());

            if (discordDataOpt.isEmpty()) {
                log.warn("SYNC: User {} not found in guild. May have left the server.", member.getIngameName());
                return false;
            }

            return applyDiscordDataToMember(member, discordDataOpt.get(), isMultiAccount);

        } catch (Exception e) {
            log.error("SYNC: General error during scheduled update for {}: {}", member.getIngameName(), e.getMessage());
            return false;
        }
    }

    private void updateSingleLinkedMember(ClanmemberEntity member, String globalName, String avatarHash, List<String> sortedRoles, ClanGroup detectedGroup) {
        member.setDiscordName(globalName);
        member.setAvatarHash(avatarHash);
        member.setDiscordRoles(sortedRoles);

        if (member.getClanGroup() == null && detectedGroup != null) {
            member.setClanGroup(detectedGroup);
        }
        clanmemberRepository.save(member);
    }

    private ClanGroup resolveClanGroup(List<String> roles) {
        if (roles == null) return null;

        boolean hasT1 = roles.contains(discordRoleService.getT1RoleId());
        boolean hasT2 = roles.contains(discordRoleService.getT2RoleId());

        if (hasT1 && !hasT2) return ClanGroup.T1;
        if (hasT2 && !hasT1) return ClanGroup.T2;
        return null;
    }

    private int compareClanRanks(ClanmemberEntity m1, ClanmemberEntity m2) {
        if (m1.getClanRank() == null && m2.getClanRank() == null) return 0;
        if (m1.getClanRank() == null) return 1;
        if (m2.getClanRank() == null) return -1;

        return m1.getClanRank().compareTo(m2.getClanRank());
    }

    private List<String> resolveRoleNamesForUser(String discordId) {
        List<ClanmemberEntity> linkedMembers = clanmemberRepository.findAllByDiscordId(discordId);
        if (linkedMembers.isEmpty()) {
            return List.of("No Discord Roles Found");
        }

        List<String> roleIds = linkedMembers.get(0).getDiscordRoles();
        if (roleIds == null || roleIds.isEmpty()) {
            return List.of("No Discord Roles Found");
        }

        return roleIds.stream()
                .map(discordRoleService::getRoleName)
                .toList();
    }

    public String buildAvatarUrl(String discordId, String avatarHash) {
        boolean hasValidHash = StringUtils.hasText(avatarHash) && !"null".equalsIgnoreCase(avatarHash);

        if (StringUtils.hasText(discordId) && hasValidHash) {
            return "https://cdn.discordapp.com/avatars/" + discordId + "/" + avatarHash + ".png";
        }

        if (StringUtils.hasText(discordId)) {
            try {
                long id = Long.parseLong(discordId);
                long index = (id >> 22) % 6;
                return "https://cdn.discordapp.com/embed/avatars/" + index + ".png";
            } catch (NumberFormatException e) {
                log.debug("Could not parse Discord ID {} for default avatar generation.", discordId);
            }
        }

        return "/images/placeholder.png";
    }
}