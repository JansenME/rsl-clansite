package com.rsl.clansite.service;

import com.rsl.clansite.model.entity.ClanmemberEntity;
import com.rsl.clansite.model.enums.AuditAction;
import com.rsl.clansite.repository.ClanmemberRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service("rosterService")
public class RosterService {
    private final ClanmemberService clanmemberService;
    private final ClanmemberRepository clanmemberRepository;
    private final AuditLogService auditLogService;

    public RosterService(ClanmemberService clanmemberService,
                         ClanmemberRepository clanmemberRepository,
                         AuditLogService auditLogService) {
        this.clanmemberService = clanmemberService;
        this.clanmemberRepository = clanmemberRepository;
        this.auditLogService = auditLogService;
    }

    /**
     * Updates the roster.
     * Security:
     * - hasRole('COORDINATOR') -> Automatically includes ADMIN and OWNER via Hierarchy Bean.
     * - @rosterService.isOwner(...) -> Allows regular members to edit ONLY their own data.
     */
    @Transactional
    @PreAuthorize("hasRole('COORDINATOR') or @rosterService.isOwner(#targetMemberId, #authentication)")
    public void updateRoster(String targetMemberId, List<String> championIds, Authentication authentication) {
        ClanmemberEntity member = clanmemberService.getMemberById(targetMemberId);

        // Full Overwrite
        member.setRosterChampionIds(championIds != null ? championIds : List.of());

        clanmemberRepository.save(member);

        int count = championIds != null ? championIds.size() : 0;
        auditLogService.logAction(
                authentication,
                AuditAction.MEMBER_UPDATE,
                member.getIngameName(),
                "Updated Personal Roster. Total Champions: " + count
        );
    }


    public boolean isOwner(String targetMemberId, Authentication authentication) {
        if (targetMemberId == null || authentication == null) return false;

        try {
            // 1. Get the target member
            ClanmemberEntity member = clanmemberService.getMemberById(targetMemberId);

            // 2. Get current user's Discord ID
            String currentDiscordId = ((OAuth2User) authentication.getPrincipal()).getAttribute("id");

            // 3. Compare
            return currentDiscordId != null && currentDiscordId.equals(member.getDiscordId());

        } catch (Exception e) {
            log.warn("Security Check Failed for Roster Update: {}", e.getMessage());
            return false;
        }
    }
}
