package com.rsl.clansite.service;

import com.rsl.clansite.client.DiscordApiClient;
import com.rsl.clansite.exceptions.UnlinkedAccountException;
import com.rsl.clansite.model.ClanmemberViewData;
import com.rsl.clansite.model.dto.MemberLookupResult;
import com.rsl.clansite.model.dto.NewClanmemberDTO;
import com.rsl.clansite.model.entity.ClanmemberEntity;
import com.rsl.clansite.model.enums.AuditAction;
import com.rsl.clansite.model.enums.ClanGroup;
import com.rsl.clansite.model.enums.ClanRank;
import com.rsl.clansite.repository.ClanmemberRepository;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ClanmemberService {
    private final ClanmemberRepository clanmemberRepository;
    private final DiscordRoleService discordRoleService;
    private final AuditLogService auditLogService;
    private final DiscordApiClient discordApiClient;
    private final SiteAssetService siteAssetService;

    @Autowired
    public ClanmemberService(final ClanmemberRepository clanmemberRepository,
                             final DiscordRoleService discordRoleService,
                             final AuditLogService auditLogService,
                             final DiscordApiClient discordApiClient,
                             final SiteAssetService siteAssetService) {
        this.clanmemberRepository = clanmemberRepository;
        this.discordRoleService = discordRoleService;
        this.auditLogService = auditLogService;
        this.discordApiClient = discordApiClient;
        this.siteAssetService = siteAssetService;
    }

    public String manageActiveMemberSession(HttpSession session, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        String currentDiscordId = authentication.getName();
        List<ClanmemberEntity> linkedMembers = getLinkedClanmembers(currentDiscordId);
        String activeMemberId = (String) session.getAttribute("ACTIVE_MEMBER_ID");

        if (activeMemberId == null && !linkedMembers.isEmpty()) {
            activeMemberId = linkedMembers.get(0).getId().toHexString();
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
                    roles.contains(DiscordRoleService.T1_ROLE_ID) &&
                    roles.contains(DiscordRoleService.T2_ROLE_ID)) {
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

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void updateAllClanmemberDiscordRoles() {
        log.info("Starting scheduled Discord role verification job.");

        siteAssetService.syncFavicon();

        List<ClanmemberEntity> allLinkedMembers = clanmemberRepository.findAllByDiscordIdIsNotNull();

        Map<String, List<ClanmemberEntity>> membersByDiscordId = allLinkedMembers.stream()
                .collect(Collectors.groupingBy(ClanmemberEntity::getDiscordId));

        int membersUpdated = 0;

        for (Map.Entry<String, List<ClanmemberEntity>> entry : membersByDiscordId.entrySet()) {
            List<ClanmemberEntity> userAccounts = entry.getValue();
            boolean isMultiAccount = userAccounts.size() > 1;

            for (ClanmemberEntity member : userAccounts) {
                if (tryUpdateMemberRoles(member, isMultiAccount)) {
                    membersUpdated++;
                }
            }
        }

        log.info("Completed scheduled role verification. Total members checked: {}, Updated: {}",
                allLinkedMembers.size(), membersUpdated);
    }

    public void linkClanmember(final String discordId, final String globalName, final String avatarHash, final List<String> currentDiscordRoles) {
        List<ClanmemberEntity> linkedMembers = clanmemberRepository.findAllByDiscordId(discordId);

        if (linkedMembers.isEmpty()) {
            return;
        }

        List<String> sortedRoles = discordRoleService.sortRoles(currentDiscordRoles);
        ClanGroup detectedGroup = resolveClanGroup(currentDiscordRoles);

        for (ClanmemberEntity member : linkedMembers) {
            updateSingleLinkedMember(member, globalName, avatarHash, sortedRoles, detectedGroup);
        }
    }

    public List<ClanmemberEntity> getLinkedClanmembers(final String discordId) {
        if (discordId == null) return List.of();

        return clanmemberRepository.findAllByDiscordId(discordId);
    }

    public List<ClanmemberEntity> findAllClanmemberEntities() {
        List<ClanmemberEntity> members = clanmemberRepository.findAll();
        members.sort(this::compareClanRanks);
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
        newMember.setClanRank(dto.getClanRank() != null ? dto.getClanRank().name() : ClanRank.SOLDIER.name());
        newMember.setClanGroup(dto.getClanGroup());
        newMember.setAvatarHash(dto.getAvatarHash());
        newMember.setDiscordRoles(dto.getDiscordRoles() != null ? dto.getDiscordRoles() : List.of());
        newMember.setChampions(List.of());

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
        String activeId = (String) session.getAttribute("ACTIVE_MEMBER_ID");
        if (id.equals(activeId)) {
            session.removeAttribute("ACTIVE_MEMBER_ID");
        }

        Optional<ClanmemberEntity> memberToDelete = clanmemberRepository.findById(new ObjectId(id));
        String targetName = memberToDelete.map(ClanmemberEntity::getIngameName).orElse("Unknown ID: " + id);

        clanmemberRepository.deleteById(new ObjectId(id));

        auditLogService.logAction(
                authentication,
                AuditAction.MEMBER_DELETE,
                targetName,
                "Deleted from Roster List"
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

        if (entity.getClanRank() != null) {
            dto.setClanRank(ClanRank.valueOf(entity.getClanRank()));
        }

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

        member.setIngameName(dto.getIngameName());
        member.setClanRank(dto.getClanRank().name());
        member.setClanGroup(dto.getClanGroup());

        clanmemberRepository.save(member);

        auditLogService.logAction(
                authentication,
                AuditAction.MEMBER_UPDATE,
                member.getIngameName(),
                "Updated details for: " + member.getIngameName()
        );
    }

    private boolean tryUpdateMemberRoles(ClanmemberEntity member, boolean isMultiAccount) {
        try {
            if (!StringUtils.hasText(member.getDiscordId())) {
                return false;
            }

            Optional<NewClanmemberDTO> discordDataOpt = discordApiClient.getDiscordMember(member.getDiscordId());

            if (discordDataOpt.isEmpty()) {
                log.warn("User {} not found in guild. May have left the server.", member.getDiscordId());
                return false;
            }

            NewClanmemberDTO discordData = discordDataOpt.get();
            List<String> sortedRoles = discordRoleService.sortRoles(discordData.getDiscordRoles());
            String newAvatarHash = discordData.getAvatarHash();

            ClanGroup newDetectedGroup = resolveClanGroup(sortedRoles);

            boolean rolesChanged = !sortedRoles.equals(member.getDiscordRoles());
            boolean avatarChanged = member.getAvatarHash() == null || !newAvatarHash.equals(member.getAvatarHash());
            boolean groupChanged = false;

            if (!isMultiAccount && newDetectedGroup != null && !newDetectedGroup.equals(member.getClanGroup())) {
                member.setClanGroup(newDetectedGroup);
                groupChanged = true;
                log.debug("Clan Group auto-updated for single-account member: {} -> {}", member.getDiscordName(), newDetectedGroup);
            }

            if (rolesChanged) {
                member.setDiscordRoles(sortedRoles);
            }

            if (avatarChanged) {
                member.setAvatarHash(newAvatarHash);
            }

            if (rolesChanged || avatarChanged || groupChanged) {
                clanmemberRepository.save(member);
                return true;
            }
        } catch (Exception e) {
            log.error("General error during scheduled update for {}: {}", member.getDiscordId(), e.getMessage());
        }
        return false;
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

        boolean hasT1 = roles.contains(DiscordRoleService.T1_ROLE_ID);
        boolean hasT2 = roles.contains(DiscordRoleService.T2_ROLE_ID);

        if (hasT1 && !hasT2) return ClanGroup.T1;
        if (hasT2 && !hasT1) return ClanGroup.T2;
        return null;
    }

    private int compareClanRanks(ClanmemberEntity m1, ClanmemberEntity m2) {
        if (m1.getClanRank() == null && m2.getClanRank() == null) return 0;
        if (m1.getClanRank() == null) return 1;
        if (m2.getClanRank() == null) return -1;

        return ClanRank.valueOf(m1.getClanRank()).compareTo(ClanRank.valueOf(m2.getClanRank()));
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

    private String buildAvatarUrl(String discordId, String avatarHash) {
        if (discordId != null && avatarHash != null) {
            return "https://cdn.discordapp.com/avatars/" + discordId + "/" + avatarHash + ".png";
        }
        return null;
    }
}