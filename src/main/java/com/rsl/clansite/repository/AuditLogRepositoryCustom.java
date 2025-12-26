package com.rsl.clansite.repository;

import com.rsl.clansite.model.entity.AuditLogEntity;
import com.rsl.clansite.model.enums.AuditAction;

import java.time.LocalDateTime;
import java.util.List;

public interface AuditLogRepositoryCustom {
    List<AuditLogEntity> searchAuditLogs(
            LocalDateTime startDate,
            LocalDateTime endDate,
            String actorName,
            AuditAction action,
            String target
    );
}
