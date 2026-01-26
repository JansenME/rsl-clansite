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
import java.util.List;
import java.util.Optional;

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
     * Checks if there is an active global notice that the member hasn't seen yet.
     */
    public Optional<NoticeEntity> getUnseenNotice(ClanmemberEntity member) {
        if (member == null) {
            return Optional.empty();
        }

        // 1. Get the latest global active notice
        Optional<NoticeEntity> latestNoticeOpt = noticeRepository.findFirstByActiveTrueOrderByCreatedAtDesc();

        if (latestNoticeOpt.isEmpty()) {
            return Optional.empty();
        }

        NoticeEntity latestNotice = latestNoticeOpt.get();
        String latestId = latestNotice.getId().toHexString();

        // 2. Compare with member's last seen ID
        if (latestId.equals(member.getLastSeenNoticeId())) {
            return Optional.empty();
        }

        // 3. Return the notice
        return Optional.of(latestNotice);
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