package com.rsl.clansite.service;

import com.rsl.clansite.model.entity.ClanmemberEntity;
import com.rsl.clansite.model.entity.NoticeEntity;
import com.rsl.clansite.repository.ClanmemberRepository;
import com.rsl.clansite.repository.NoticeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class NoticeService {

    private final NoticeRepository noticeRepository;
    private final ClanmemberRepository clanmemberRepository;

    public NoticeService(NoticeRepository noticeRepository, ClanmemberRepository clanmemberRepository) {
        this.noticeRepository = noticeRepository;
        this.clanmemberRepository = clanmemberRepository;
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
     * Updates ALL linked accounts for this Discord ID so the user doesn't see the notice again
     * regardless of which sub-account they switch to.
     */
    public void markNoticeAsSeen(String discordId, String noticeId) {
        // Find all accounts belonging to this Discord User
        List<ClanmemberEntity> linkedAccounts = clanmemberRepository.findAllByDiscordId(discordId);

        if (linkedAccounts.isEmpty()) {
            log.warn("Attempted to acknowledge notice for unknown Discord ID: {}", discordId);
            return;
        }

        // Update all of them
        for (ClanmemberEntity member : linkedAccounts) {
            member.setLastSeenNoticeId(noticeId);
        }

        // Save all changes
        clanmemberRepository.saveAll(linkedAccounts);
        log.debug("Updated {} accounts for Discord ID {} to acknowledge notice {}", linkedAccounts.size(), discordId, noticeId);
    }
}