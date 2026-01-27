package com.rsl.clansite.service;

import com.rsl.clansite.model.entity.ClanmemberEntity;
import com.rsl.clansite.model.entity.NoticeEntity;
import com.rsl.clansite.model.enums.AuditAction;
import com.rsl.clansite.repository.ClanmemberRepository;
import com.rsl.clansite.repository.NoticeRepository;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class NoticeService {

    private final NoticeRepository noticeRepository;
    private final ClanmemberRepository clanmemberRepository;
    private final AuditLogService auditLogService;

    public NoticeService(NoticeRepository noticeRepository, ClanmemberRepository clanmemberRepository, AuditLogService auditLogService) {
        this.noticeRepository = noticeRepository;
        this.clanmemberRepository = clanmemberRepository;
        this.auditLogService = auditLogService;
    }

    /**
     * Returns a list of all active notices the user hasn't seen yet.
     * Logic: Fetch all active notices, and collect them until we hit the ID the user last saw.
     */
    public List<NoticeEntity> getUnseenNotices(ClanmemberEntity member) {
        if (member == null) {
            return List.of();
        }

        List<NoticeEntity> allActive = noticeRepository.findAllByActiveTrueOrderByCreatedAtDesc();
        String lastSeenId = member.getLastSeenNoticeId();

        // If they've seen nothing (null or blank), show everything active
        if (lastSeenId == null || lastSeenId.isBlank()) {
            return allActive;
        }

        List<NoticeEntity> unseen = new ArrayList<>();
        for (NoticeEntity notice : allActive) {
            // We iterate from newest to oldest.
            // If we hit the notice the user has already seen, we stop.
            if (notice.getId().toHexString().equals(lastSeenId)) {
                break;
            }
            unseen.add(notice);
        }

        return unseen;
    }

    /**
     * Updates ALL linked accounts for this Discord ID.
     */
    public void markNoticeAsSeen(String discordId, String noticeId) {
        List<ClanmemberEntity> linkedAccounts = clanmemberRepository.findAllByDiscordId(discordId);

        if (linkedAccounts.isEmpty()) {
            log.warn("Attempted to acknowledge notice for unknown Discord ID: {}", discordId);
            return;
        }

        for (ClanmemberEntity member : linkedAccounts) {
            member.setLastSeenNoticeId(noticeId);
        }

        clanmemberRepository.saveAll(linkedAccounts);
        log.debug("Updated {} accounts for Discord ID {} to acknowledge notice {}", linkedAccounts.size(), discordId, noticeId);
    }

    // --- ADMIN METHODS ---

    public List<NoticeEntity> getAllNotices() {
        return noticeRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    public void createNotice(String title, String content, String author, Authentication authentication) {
        NoticeEntity notice = new NoticeEntity();
        notice.setTitle(title);
        notice.setContent(content);
        notice.setCreatedBy(author);
        notice.setCreatedAt(LocalDateTime.now());
        notice.setActive(true); // Default to active

        auditLogService.logAction(authentication, AuditAction.CREATE_NOTICE, "Notice Board", "Created a website notice");

        noticeRepository.save(notice);
    }

    public void toggleActive(String id) {
        noticeRepository.findById(new ObjectId(id)).ifPresent(notice -> {
            notice.setActive(!notice.isActive());
            noticeRepository.save(notice);
        });
    }
}