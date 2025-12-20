package com.rsl.clansite.service;

import com.rsl.clansite.model.entity.AuditLogEntity;
import com.rsl.clansite.model.entity.ClanmemberEntity;
import com.rsl.clansite.model.enums.AuditAction;
import com.rsl.clansite.repository.AuditLogRepository;
import com.rsl.clansite.repository.ClanmemberRepository;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class AuditLogService {
    private final AuditLogRepository auditLogRepository;
    private final ClanmemberRepository clanmemberRepository;

    public AuditLogService(AuditLogRepository auditLogRepository, ClanmemberRepository clanmemberRepository) {
        this.auditLogRepository = auditLogRepository;
        this.clanmemberRepository = clanmemberRepository;
    }

    public void logAction(Authentication authentication, AuditAction action, String target, String details) {
        String actorId = "SYSTEM";
        String actorName = "System";

        if (authentication != null && authentication.getPrincipal() instanceof OAuth2User oauth2User) {
            actorId = oauth2User.getAttribute("id");
            actorName = resolveActorName(oauth2User, actorId);
        }

        AuditLogEntity logEntry = new AuditLogEntity(
                ObjectId.get(),
                LocalDateTime.now(),
                actorId,
                actorName,
                action,
                target,
                details
        );

        auditLogRepository.save(logEntry);
        log.info("Audit Log recorded: [{}] {} performed by {}", action, target, actorName);
    }

    public List<AuditLogEntity> getAllLogs() {
        return auditLogRepository.findAllByOrderByTimestampDesc();
    }

    private String resolveActorName(OAuth2User oauth2User, String actorId) {
        String sessionName = oauth2User.getAttribute("global_name");
        String resolvedName = (sessionName != null) ? sessionName : "Unknown Admin";

        List<ClanmemberEntity> adminEntry = clanmemberRepository.findAllByDiscordId(actorId);

        if (!adminEntry.isEmpty()) {
            ClanmemberEntity admin = adminEntry.get(0);

            if (admin.getPlayerNickname() != null && !admin.getPlayerNickname().isBlank()) {
                return admin.getPlayerNickname();
            }
            if (admin.getDiscordName() != null && !admin.getDiscordName().isBlank()) {
                return admin.getDiscordName();
            }
        }

        return resolvedName;
    }
}
