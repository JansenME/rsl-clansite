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

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
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

    /**
     * Standard logging for User-initiated actions (via Controller).
     */
    public void logAction(Authentication authentication, AuditAction action, String target, String details) {
        String actorId = "SYSTEM";
        String actorName = "System";

        if (authentication != null && authentication.getPrincipal() instanceof OAuth2User oauth2User) {
            actorId = oauth2User.getAttribute("id");
            actorName = resolveActorName(oauth2User, actorId);
        }

        saveLog(actorId, actorName, action, target, details);
    }

    /**
     * Logging for System or Background tasks (Scheduler, Backup, Scraper) where no user session exists.
     */
    public void logSystemAction(AuditAction action, String target, String details) {
        saveLog("SYSTEM", "System", action, target, details);
    }

    private void saveLog(String actorId, String actorName, AuditAction action, String target, String details) {
        AuditLogEntity logEntry = new AuditLogEntity(
                ObjectId.get(),
                LocalDateTime.now(ZoneOffset.UTC), // Explicitly store as UTC
                actorId,
                actorName,
                action,
                target,
                details
        );
        auditLogRepository.save(logEntry);
        log.debug("Audit Log recorded: [{}] {} - {} performed by {}", action, target, details, actorName);
    }

    public List<AuditLogEntity> searchLogs(String userTimeZone, LocalDate fromDate, LocalDate toDate, String actor, AuditAction action, String target) {
        // 1. Resolve ZoneId
        ZoneId zoneId;
        try {
            zoneId = (userTimeZone != null && !userTimeZone.isEmpty())
                    ? ZoneId.of(userTimeZone)
                    : ZoneId.of("UTC");
        } catch (Exception e) {
            zoneId = ZoneId.of("UTC");
        }

        // 2. Calculate Start Time (Midnight in User's Zone -> Converted to UTC LocalDateTime)
        LocalDateTime start = null;
        if (fromDate != null) {
            Instant fromInstant = fromDate.atStartOfDay(zoneId).toInstant();
            start = LocalDateTime.ofInstant(fromInstant, ZoneOffset.UTC);
        }

        // 3. Calculate End Time (End of Day in User's Zone -> Converted to UTC LocalDateTime)
        LocalDateTime end = null;
        if (toDate != null) {
            Instant toInstant = toDate.atTime(LocalTime.MAX).atZone(zoneId).toInstant();
            end = LocalDateTime.ofInstant(toInstant, ZoneOffset.UTC);
        }

        // 4. Query Repository using UTC times
        return auditLogRepository.searchAuditLogs(start, end, actor, action, target);
    }

    public void deleteLogEntry(String id) {
        if (id == null || !ObjectId.isValid(id)) {
            log.warn("Attempted to delete audit log with invalid ID: {}", id);
            return;
        }
        auditLogRepository.deleteById(new ObjectId(id));
        log.info("Audit log entry {} deleted manually by OWNER.", id);
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