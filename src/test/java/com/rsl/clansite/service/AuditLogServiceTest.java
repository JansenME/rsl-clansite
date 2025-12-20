package com.rsl.clansite.service;

import com.rsl.clansite.model.entity.AuditLogEntity;
import com.rsl.clansite.model.entity.ClanmemberEntity;
import com.rsl.clansite.model.enums.AuditAction;
import com.rsl.clansite.repository.AuditLogRepository;
import com.rsl.clansite.repository.ClanmemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {
    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private ClanmemberRepository clanmemberRepository;

    @InjectMocks
    private AuditLogService auditLogService;

    @Test
    @DisplayName("logAction should log as 'System' when authentication is null")
    void logAction_ShouldLogAsSystem_WhenAuthIsNull() {
        auditLogService.logAction(null, AuditAction.MEMBER_ADD, "Target", "Details");

        ArgumentCaptor<AuditLogEntity> captor = ArgumentCaptor.forClass(AuditLogEntity.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLogEntity savedLog = captor.getValue();
        assertEquals("SYSTEM", savedLog.getActorDiscordId());
        assertEquals("System", savedLog.getActorDiscordName());
    }

    @Test
    @DisplayName("logAction should use OAuth global_name when admin is not in Clanmember DB")
    void logAction_ShouldUseGlobalName_WhenMemberNotFound() {
        String discordId = "123";
        String globalName = "AdminUser";

        OAuth2User oauth2User = mock(OAuth2User.class);
        when(oauth2User.getAttribute("id")).thenReturn(discordId);
        when(oauth2User.getAttribute("global_name")).thenReturn(globalName);

        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(oauth2User);

        when(clanmemberRepository.findAllByDiscordId(discordId)).thenReturn(List.of());

        auditLogService.logAction(auth, AuditAction.MEMBER_DELETE, "Target", "Details");

        ArgumentCaptor<AuditLogEntity> captor = ArgumentCaptor.forClass(AuditLogEntity.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLogEntity savedLog = captor.getValue();
        assertEquals(discordId, savedLog.getActorDiscordId());
        assertEquals(globalName, savedLog.getActorDiscordName());
    }

    @Test
    @DisplayName("logAction should use Player Nickname if Admin exists in DB")
    void logAction_ShouldUseNickname_WhenMemberExists() {
        String discordId = "456";

        OAuth2User oauth2User = mock(OAuth2User.class);
        when(oauth2User.getAttribute("id")).thenReturn(discordId);
        when(oauth2User.getAttribute("global_name")).thenReturn("IgnoredGlobalName");

        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(oauth2User);

        ClanmemberEntity adminEntity = new ClanmemberEntity();
        adminEntity.setPlayerNickname("SuperAdmin"); // Preferred name
        when(clanmemberRepository.findAllByDiscordId(discordId)).thenReturn(List.of(adminEntity));

        auditLogService.logAction(auth, AuditAction.CHAMPION_UPDATE, "Target", "Details");

        ArgumentCaptor<AuditLogEntity> captor = ArgumentCaptor.forClass(AuditLogEntity.class);
        verify(auditLogRepository).save(captor.capture());

        assertEquals("SuperAdmin", captor.getValue().getActorDiscordName());
    }

    @Test
    @DisplayName("logAction should fallback to Discord Name if Nickname is blank in DB")
    void logAction_ShouldFallbackToDiscordName_WhenNicknameBlank() {
        String discordId = "789";

        OAuth2User oauth2User = mock(OAuth2User.class);
        when(oauth2User.getAttribute("id")).thenReturn(discordId);
        when(oauth2User.getAttribute("global_name")).thenReturn("IgnoredGlobalName");

        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(oauth2User);

        ClanmemberEntity adminEntity = new ClanmemberEntity();
        adminEntity.setPlayerNickname(""); // Blank
        adminEntity.setDiscordName("FallbackDiscordName"); // Fallback
        when(clanmemberRepository.findAllByDiscordId(discordId)).thenReturn(List.of(adminEntity));

        auditLogService.logAction(auth, AuditAction.MEMBER_ADD, "Target", "Details");

        ArgumentCaptor<AuditLogEntity> captor = ArgumentCaptor.forClass(AuditLogEntity.class);
        verify(auditLogRepository).save(captor.capture());

        assertEquals("FallbackDiscordName", captor.getValue().getActorDiscordName());
    }

    @Test
    @DisplayName("getAllLogs should delegate to repository")
    void getAllLogs_ShouldCallRepository() {
        auditLogService.getAllLogs();
        verify(auditLogRepository).findAllByOrderByTimestampDesc();
    }
}