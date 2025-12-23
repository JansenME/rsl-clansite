package com.rsl.clansite.service;

import com.rsl.clansite.client.DiscordApiClient;
import com.rsl.clansite.exceptions.UnlinkedAccountException;
import com.rsl.clansite.model.dto.NewClanmemberDTO;
import com.rsl.clansite.model.entity.ClanmemberEntity;
import com.rsl.clansite.model.enums.AuditAction;
import com.rsl.clansite.model.enums.ClanGroup;
import com.rsl.clansite.model.enums.ClanRank;
import com.rsl.clansite.repository.ClanmemberRepository;
import jakarta.servlet.http.HttpSession;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClanmemberServiceTest {
    @Mock
    private ClanmemberRepository clanmemberRepository;

    @Mock
    private DiscordRoleService discordRoleService;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private DiscordApiClient discordApiClient;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private ClanmemberService clanmemberService;

    private ClanmemberEntity leader;
    private ClanmemberEntity soldier;
    private ClanmemberEntity deputy;

    @BeforeEach
    void setUp() {
        leader = new ClanmemberEntity();
        leader.setId(ObjectId.get());
        leader.setIngameName("LeaderUser");
        leader.setClanRank(ClanRank.LEADER.name());

        deputy = new ClanmemberEntity();
        deputy.setId(ObjectId.get());
        deputy.setIngameName("DeputyUser");
        deputy.setClanRank(ClanRank.DEPUTY.name());

        soldier = new ClanmemberEntity();
        soldier.setId(ObjectId.get());
        soldier.setIngameName("SoldierUser");
        soldier.setClanRank(ClanRank.SOLDIER.name());
    }

    @Test
    @DisplayName("saveNewClanmember - Should save entity and log Ingame Name in audit details")
    void saveNewClanmember_ShouldLogCorrectly() {
        NewClanmemberDTO dto = new NewClanmemberDTO();
        dto.setIngameName("TestPlayer");
        dto.setClanRank(ClanRank.SOLDIER);
        dto.setClanGroup(ClanGroup.T1);

        clanmemberService.saveNewClanmember(dto, authentication);

        verify(clanmemberRepository).save(any(ClanmemberEntity.class));

        verify(auditLogService).logAction(
                eq(authentication),
                eq(AuditAction.MEMBER_ADD),
                eq("TestPlayer"),
                eq("Manually added to Roster: TestPlayer")
        );
    }

    @Test
    @DisplayName("findAll should return members sorted by Rank (Leader > Deputy > Soldier)")
    void findAllClanmemberEntities_ShouldReturnSortedList() {
        when(clanmemberRepository.findAll()).thenReturn(Arrays.asList(soldier, leader, deputy));

        List<ClanmemberEntity> result = clanmemberService.findAllClanmemberEntities();

        assertEquals(3, result.size());
        assertEquals(ClanRank.LEADER.name(), result.get(0).getClanRank());
        assertEquals(ClanRank.DEPUTY.name(), result.get(1).getClanRank());
        assertEquals(ClanRank.SOLDIER.name(), result.get(2).getClanRank());
    }

    @Test
    @DisplayName("saveNewClanmember should save entity and log to Audit Log")
    void saveNewClanmember_ShouldSaveAndLogAudit() {
        NewClanmemberDTO dto = new NewClanmemberDTO();
        dto.setDiscordId("12345");
        dto.setIngameName("NewPlayer");
        dto.setClanGroup(ClanGroup.T1);

        Authentication authentication = mock(Authentication.class);

        clanmemberService.saveNewClanmember(dto, authentication);

        ArgumentCaptor<ClanmemberEntity> entityCaptor = ArgumentCaptor.forClass(ClanmemberEntity.class);
        verify(clanmemberRepository).save(entityCaptor.capture());

        ClanmemberEntity savedEntity = entityCaptor.getValue();
        assertEquals("NewPlayer", savedEntity.getIngameName());
        assertEquals("12345", savedEntity.getDiscordId());

        verify(auditLogService).logAction(
                eq(authentication),
                eq(AuditAction.MEMBER_ADD),
                eq("NewPlayer"),
                contains("Manually added to Roster")
        );
    }

    @Test
    @DisplayName("deleteById should lookup name first, delete, and log to Audit Log")
    void deleteById_ShouldDeleteAndLogAudit() {
        String targetId = leader.getId().toHexString();
        HttpSession session = mock(HttpSession.class);
        Authentication authentication = mock(Authentication.class);

        when(clanmemberRepository.findById(leader.getId())).thenReturn(Optional.of(leader));

        clanmemberService.deleteById(targetId, session, authentication);

        verify(clanmemberRepository).deleteById(leader.getId());

        verify(auditLogService).logAction(
                eq(authentication),
                eq(AuditAction.MEMBER_DELETE),
                eq("LeaderUser"),
                anyString()
        );
    }

    @Test
    @DisplayName("getLinkedClanmembers should throw exception if no members found")
    void getLinkedClanmembers_ShouldThrowException_WhenEmpty() {
        String discordId = "99999";
        when(clanmemberRepository.findAllByDiscordId(discordId)).thenReturn(List.of());

        assertThrows(
                UnlinkedAccountException.class,
                () -> clanmemberService.getLinkedClanmembers(discordId)
        );
    }

    @Test
    @DisplayName("isPlayerIngameNameInUse should return true if repository finds a match")
    void isPlayerIngameNameInUse_ShouldReturnTrue_WhenExists() {
        String name = "TakenName";
        when(clanmemberRepository.existsByIngameName(name)).thenReturn(true);

        boolean result = clanmemberService.isPlayerIngameNameInUse(name);

        assertTrue(result);
    }

    @Test
    @DisplayName("linkClanmember should update ClanGroup based on Discord Roles")
    void linkClanmember_ShouldUpdateClanGroup_WhenRoleMatches() {
        String discordId = "12345";
        String globalName = "NewGlobalName";
        String avatar = "hash123";
        List<String> roles = List.of(DiscordRoleService.T1_ROLE_ID);

        ClanmemberEntity existingMember = new ClanmemberEntity();
        existingMember.setDiscordId(discordId);
        existingMember.setClanGroup(null);

        when(clanmemberRepository.findAllByDiscordId(discordId)).thenReturn(List.of(existingMember));
        when(discordRoleService.sortRoles(roles)).thenReturn(roles);

        clanmemberService.linkClanmember(discordId, globalName, avatar, roles);

        ArgumentCaptor<ClanmemberEntity> captor = ArgumentCaptor.forClass(ClanmemberEntity.class);
        verify(clanmemberRepository).save(captor.capture());

        ClanmemberEntity updatedMember = captor.getValue();
        assertEquals(ClanGroup.T1, updatedMember.getClanGroup());
        assertEquals(globalName, updatedMember.getDiscordName());
    }

    @Test
    @DisplayName("getUserViewData should return 'Unknown User' when authentication is null")
    void getUserViewData_ShouldReturnGuest_WhenAuthIsNull() {
        var result = clanmemberService.getUserViewData(null);

        assertNull(result.getDiscordUserName());
        assertTrue(result.getDiscordUserRoles().isEmpty());
    }

    @Test
    @DisplayName("getUserViewData should return role names when user is linked")
    void getUserViewData_ShouldReturnRoleNames_WhenLinked() {
        String discordId = "12345";
        String roleId = "role_99";

        org.springframework.security.oauth2.core.user.OAuth2User oauth2User = mock(org.springframework.security.oauth2.core.user.OAuth2User.class);
        when(oauth2User.getAttribute("id")).thenReturn(discordId);
        when(oauth2User.getAttribute("global_name")).thenReturn("TestUser");
        when(oauth2User.getAttribute("avatar")).thenReturn("avatar123");

        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(oauth2User);

        ClanmemberEntity member = new ClanmemberEntity();
        member.setDiscordId(discordId);
        member.setDiscordRoles(List.of(roleId));
        when(clanmemberRepository.findAllByDiscordId(discordId)).thenReturn(List.of(member));

        when(discordRoleService.getRoleName(roleId)).thenReturn("Cool Role Name");

        var result = clanmemberService.getUserViewData(auth);

        assertEquals("TestUser", result.getDiscordUserName());
        assertEquals(1, result.getDiscordUserRoles().size());
        assertEquals("Cool Role Name", result.getDiscordUserRoles().get(0));
    }

    @Test
    @DisplayName("isDiscordIdInRoster should return true if count > 0")
    void isDiscordIdInRoster_ShouldReturnTrue_WhenFound() {
        when(clanmemberRepository.countByDiscordId("123")).thenReturn(1L);
        assertTrue(clanmemberService.isDiscordIdInRoster("123"));
    }

    @Test
    @DisplayName("lookupDiscordUser should Assign T1 Group if user has T1 role")
    void lookupDiscordUser_ShouldAssignT1_WhenRolePresent() {
        String discordId = "555";
        NewClanmemberDTO mockDto = new NewClanmemberDTO();
        mockDto.setDiscordId(discordId);
        mockDto.setDiscordRoles(List.of(DiscordRoleService.T1_ROLE_ID));

        when(discordApiClient.getDiscordMember(discordId)).thenReturn(Optional.of(mockDto));

        NewClanmemberDTO result = clanmemberService.lookupDiscordUser(discordId);

        assertEquals(ClanGroup.T1, result.getClanGroup());
        assertEquals(discordId, result.getDiscordId());
    }

    @Test
    @DisplayName("lookupDiscordUser should throw RuntimeException if client returns empty")
    void lookupDiscordUser_ShouldThrowException_WhenNotFound() {
        String discordId = "999";
        when(discordApiClient.getDiscordMember(discordId)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> clanmemberService.lookupDiscordUser(discordId));
    }

    @Test
    @DisplayName("Scheduled Update: Should update member if Avatar changes")
    void updateAllClanmemberDiscordRoles_ShouldUpdate_WhenAvatarChanged() {
        ClanmemberEntity existing = new ClanmemberEntity();
        existing.setDiscordId("100");
        existing.setAvatarHash("old_hash");
        existing.setDiscordRoles(List.of("roleA"));

        when(clanmemberRepository.findAllByDiscordIdIsNotNull()).thenReturn(List.of(existing));

        NewClanmemberDTO apiData = new NewClanmemberDTO();
        apiData.setAvatarHash("new_hash"); // Changed!
        apiData.setDiscordRoles(List.of("roleA"));
        when(discordApiClient.getDiscordMember("100")).thenReturn(Optional.of(apiData));

        when(discordRoleService.sortRoles(anyList())).thenReturn(List.of("roleA"));

        clanmemberService.updateAllClanmemberDiscordRoles();

        ArgumentCaptor<ClanmemberEntity> captor = ArgumentCaptor.forClass(ClanmemberEntity.class);
        verify(clanmemberRepository).save(captor.capture());

        assertEquals("new_hash", captor.getValue().getAvatarHash());
    }

    @Test
    @DisplayName("Scheduled Update: Should catch exception for one user and continue to next")
    void updateAllClanmemberDiscordRoles_ShouldContinue_WhenOneFails() {
        ClanmemberEntity badUser = new ClanmemberEntity();
        badUser.setDiscordId("bad_id");

        ClanmemberEntity goodUser = new ClanmemberEntity();
        goodUser.setDiscordId("good_id");

        when(clanmemberRepository.findAllByDiscordIdIsNotNull()).thenReturn(List.of(badUser, goodUser));

        when(discordApiClient.getDiscordMember("bad_id")).thenThrow(new RuntimeException("API Down"));

        NewClanmemberDTO goodData = new NewClanmemberDTO();
        goodData.setAvatarHash("hash");
        goodData.setDiscordRoles(List.of());
        when(discordApiClient.getDiscordMember("good_id")).thenReturn(Optional.of(goodData));
        when(discordRoleService.sortRoles(anyList())).thenReturn(List.of());

        clanmemberService.updateAllClanmemberDiscordRoles();

        verify(clanmemberRepository, org.mockito.Mockito.times(1)).save(goodUser);
    }

    @Test
    @DisplayName("Scheduled Update: Should log warn and continue when user not found in Discord")
    void updateAllClanmemberDiscordRoles_ShouldLogWarn_WhenUserNotFound() {
        ClanmemberEntity missingMember = new ClanmemberEntity();
        missingMember.setDiscordId("missing_id");

        when(clanmemberRepository.findAllByDiscordIdIsNotNull()).thenReturn(List.of(missingMember));
        when(discordApiClient.getDiscordMember("missing_id")).thenReturn(Optional.empty());

        clanmemberService.updateAllClanmemberDiscordRoles();

        verify(clanmemberRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    @DisplayName("linkClanmember should log warning and do nothing if Discord ID not in DB")
    void linkClanmember_ShouldLogWarning_WhenRosterEmpty() {
        String discordId = "unknown_id";
        when(clanmemberRepository.findAllByDiscordId(discordId)).thenReturn(List.of());

        clanmemberService.linkClanmember(discordId, "Name", "hash", List.of());

        verify(clanmemberRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    @DisplayName("linkClanmember should detect T2 Group")
    void linkClanmember_ShouldDetectT2_WhenRolePresent() {
        String discordId = "123";
        ClanmemberEntity member = new ClanmemberEntity();
        member.setDiscordId(discordId);

        when(clanmemberRepository.findAllByDiscordId(discordId)).thenReturn(List.of(member));

        List<String> t2Roles = List.of(DiscordRoleService.T2_ROLE_ID);
        when(discordRoleService.sortRoles(t2Roles)).thenReturn(t2Roles);

        clanmemberService.linkClanmember(discordId, "Global", "hash", t2Roles);

        ArgumentCaptor<ClanmemberEntity> captor = ArgumentCaptor.forClass(ClanmemberEntity.class);
        verify(clanmemberRepository).save(captor.capture());
        assertEquals(ClanGroup.T2, captor.getValue().getClanGroup());
    }

    @Test
    @DisplayName("getLinkedClanmembers should return list when found")
    void getLinkedClanmembers_ShouldReturnList_WhenFound() {
        String discordId = "123";
        ClanmemberEntity member = new ClanmemberEntity();
        when(clanmemberRepository.findAllByDiscordId(discordId)).thenReturn(List.of(member));

        List<ClanmemberEntity> result = clanmemberService.getLinkedClanmembers(discordId);

        assertEquals(1, result.size());
        assertEquals(member, result.get(0));
    }

    @Test
    @DisplayName("getUserViewData should return null avatar URL if hash is missing")
    void getUserViewData_ShouldReturnNullUrl_WhenAvatarMissing() {
        String discordId = "123";

        OAuth2User oauth2User = mock(OAuth2User.class);
        when(oauth2User.getAttribute("id")).thenReturn(discordId);
        when(oauth2User.getAttribute("global_name")).thenReturn("User");
        when(oauth2User.getAttribute("avatar")).thenReturn(null);

        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(oauth2User);

        var result = clanmemberService.getUserViewData(auth);

        assertNull(result.getDiscordAvatarUrl());
    }

    @Test
    @DisplayName("lookupDiscordUser should correct assign T2 or Null group")
    void lookupDiscordUser_ShouldAssignCorrectGroups() {
        NewClanmemberDTO t2Dto = new NewClanmemberDTO();
        t2Dto.setDiscordRoles(List.of(DiscordRoleService.T2_ROLE_ID));
        when(discordApiClient.getDiscordMember("t2_id")).thenReturn(Optional.of(t2Dto));

        assertEquals(ClanGroup.T2, clanmemberService.lookupDiscordUser("t2_id").getClanGroup());

        NewClanmemberDTO noGroupDto = new NewClanmemberDTO();
        noGroupDto.setDiscordRoles(List.of("random_role"));
        when(discordApiClient.getDiscordMember("none_id")).thenReturn(Optional.of(noGroupDto));

        assertNull(clanmemberService.lookupDiscordUser("none_id").getClanGroup());
    }

    @Test
    @DisplayName("deleteById should remove active member ID from session if deleted")
    void deleteById_ShouldClearSession_WhenActiveMemberDeleted() {
        String idToDelete = leader.getId().toHexString();
        HttpSession session = mock(HttpSession.class);
        Authentication auth = mock(Authentication.class);

        when(session.getAttribute("ACTIVE_MEMBER_ID")).thenReturn(idToDelete);
        when(clanmemberRepository.findById(leader.getId())).thenReturn(Optional.of(leader));

        clanmemberService.deleteById(idToDelete, session, auth);

        verify(session).removeAttribute("ACTIVE_MEMBER_ID");
    }

    @Test
    @DisplayName("getUserViewData should return 'No Discord Roles Found' if linked member has empty roles list")
    void getUserViewData_ShouldReturnDefault_WhenRolesEmpty() {
        String discordId = "123";

        org.springframework.security.oauth2.core.user.OAuth2User oauth2User = mock(org.springframework.security.oauth2.core.user.OAuth2User.class);
        when(oauth2User.getAttribute("id")).thenReturn(discordId);

        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(oauth2User);

        ClanmemberEntity member = new ClanmemberEntity();
        member.setDiscordId(discordId);
        member.setDiscordRoles(List.of());

        when(clanmemberRepository.findAllByDiscordId(discordId)).thenReturn(List.of(member));

        var result = clanmemberService.getUserViewData(auth);

        assertEquals(1, result.getDiscordUserRoles().size());
        assertEquals("No Discord Roles Found", result.getDiscordUserRoles().get(0));
    }
}