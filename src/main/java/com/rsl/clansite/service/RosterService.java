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

import java.time.LocalDateTime;
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

    @Transactional
    @PreAuthorize("hasRole('COORDINATOR') or @rosterService.isOwner(#targetMemberId, #authentication)")
    public void updateRoster(String targetMemberId, List<String> championIds, Authentication authentication) {
        ClanmemberEntity member = clanmemberService.getMemberById(targetMemberId);

        member.setRosterChampionIds(championIds != null ? championIds : List.of());

        member.setRosterLastUpdated(LocalDateTime.now());
        member.setRosterUpdatedBy(resolveUpdaterName(authentication));

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
            ClanmemberEntity member = clanmemberService.getMemberById(targetMemberId);
            String currentDiscordId = ((OAuth2User) authentication.getPrincipal()).getAttribute("id");
            return currentDiscordId != null && currentDiscordId.equals(member.getDiscordId());
        } catch (Exception e) {
            log.warn("Security Check Failed for Roster Update: {}", e.getMessage());
            return false;
        }
    }

    private String resolveUpdaterName(Authentication authentication) {
        if (authentication.getPrincipal() instanceof OAuth2User oauth2User) {
            String discordId = oauth2User.getAttribute("id");

            List<ClanmemberEntity> linkedAccounts = clanmemberService.getLinkedClanmembers(discordId);
            if (!linkedAccounts.isEmpty()) {
                ClanmemberEntity actor = linkedAccounts.get(0);
                return actor.getPlayerNickname() != null ? actor.getPlayerNickname() : actor.getDiscordName();
            }

            String globalName = oauth2User.getAttribute("global_name");
            return globalName != null ? globalName : oauth2User.getAttribute("username");
        }
        return "Unknown";
    }
}
