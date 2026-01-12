package com.rsl.clansite.service;

import com.rsl.clansite.client.DiscordApiClient;
import com.rsl.clansite.exceptions.UnlinkedAccountException;
import com.rsl.clansite.model.ClanmemberViewData;
import com.rsl.clansite.model.dto.MemberLookupResult;
import com.rsl.clansite.model.dto.NewClanmemberDTO;
import com.rsl.clansite.model.entity.ClanmemberEntity;
import com.rsl.clansite.model.entity.VisitorLogEntity;
import com.rsl.clansite.model.enums.AuditAction;
import com.rsl.clansite.model.enums.ClanGroup;
import com.rsl.clansite.model.enums.ClanRank;
import com.rsl.clansite.repository.ClanmemberRepository;
import com.rsl.clansite.repository.VisitorLogRepository;
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

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClanmemberServiceTest {
    @Mock
    private ClanmemberRepository clanmemberRepository;
    @Mock
    private VisitorLogRepository visitorLogRepository;
    @Mock
    private DiscordRoleService discordRoleService;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private DiscordApiClient discordApiClient;
    @Mock
    private Authentication authentication;
    @Mock
    private SiteAssetService siteAssetService;

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

        lenient().when(discordRoleService.getT1RoleId()).thenReturn("test-t1-id");
        lenient().when(discordRoleService.getT2RoleId()).thenReturn("test-t2-id");
        lenient().when(discordRoleService.getClanLeaderRoleId()).thenReturn("test-leader-id");
        lenient().when(discordRoleService.getDeputyRoleId()).thenReturn("test-deputy-id");
        lenient().when(discordRoleService.getSiegeCoordinatorRoleId()).thenReturn("test-coordinator-id");
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
    @DisplayName("getLinkedClanmembers should return empty list if no members found (Safe for anonymous users)")
    void getLinkedClanmembers_ShouldReturnEmptyList_WhenEmpty() {
        String discordId = "99999";
        when(clanmemberRepository.findAllByDiscordId(discordId)).thenReturn(List.of());

        List<ClanmemberEntity> result = clanmemberService.getLinkedClanmembers(discordId);

        assertTrue(result.isEmpty(), "Should return empty list for unlinked user");
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
        List<String> roles = List.of(discordRoleService.getT1RoleId());

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
        mockDto.setDiscordRoles(List.of(discordRoleService.getT1RoleId()));

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

        verify(clanmemberRepository, never()).save(any());
    }

    @Test
    @DisplayName("linkClanmember should log warning and do nothing if Discord ID not in DB")
    void linkClanmember_ShouldLogWarning_WhenRosterEmpty() {
        String discordId = "unknown_id";
        when(clanmemberRepository.findAllByDiscordId(discordId)).thenReturn(List.of());

        clanmemberService.linkClanmember(discordId, "Name", "hash", List.of());

        verify(clanmemberRepository, never()).save(any());
    }

    @Test
    @DisplayName("linkClanmember should detect T2 Group")
    void linkClanmember_ShouldDetectT2_WhenRolePresent() {
        String discordId = "123";
        ClanmemberEntity member = new ClanmemberEntity();
        member.setDiscordId(discordId);

        when(clanmemberRepository.findAllByDiscordId(discordId)).thenReturn(List.of(member));

        List<String> t2Roles = List.of(discordRoleService.getT2RoleId());
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
        t2Dto.setDiscordRoles(List.of(discordRoleService.getT2RoleId()));
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

    @Test
    @DisplayName("performMemberLookup - Should return warning if user has both T1 and T2 roles")
    void performMemberLookup_WithDualRoles_ShouldReturnWarning() {
        String discordId = "dual_role_user";

        NewClanmemberDTO mockDto = new NewClanmemberDTO();
        mockDto.setDiscordId(discordId);
        mockDto.setDiscordRoles(List.of(discordRoleService.getT1RoleId(), discordRoleService.getT2RoleId()));
        when(discordApiClient.getDiscordMember(discordId)).thenReturn(Optional.of(mockDto));

        MemberLookupResult result = clanmemberService.performMemberLookup(discordId);

        assertTrue(result.isSuccess());
        assertNotNull(result.getWarningMessage());
        assertTrue(result.getWarningMessage().contains("has both T1 and T2 roles"));
    }

    @Test
    @DisplayName("performMemberLookup - Should return warning if user is already in roster")
    void performMemberLookup_DuplicateUser_ShouldReturnWarning() {
        String discordId = "existing_user";

        NewClanmemberDTO mockDto = new NewClanmemberDTO();
        mockDto.setDiscordRoles(List.of());
        when(discordApiClient.getDiscordMember(discordId)).thenReturn(Optional.of(mockDto));

        when(clanmemberRepository.countByDiscordId(discordId)).thenReturn(1L);

        MemberLookupResult result = clanmemberService.performMemberLookup(discordId);

        assertTrue(result.isSuccess());
        assertNotNull(result.getWarningMessage());
        assertTrue(result.getWarningMessage().contains("already in the roster"));
    }

    @Test
    @DisplayName("performMemberLookup - Should return Failure Result on API Error")
    void performMemberLookup_ApiError_ShouldReturnFailure() {
        String discordId = "bad_user";
        when(discordApiClient.getDiscordMember(discordId)).thenReturn(Optional.empty());

        MemberLookupResult result = clanmemberService.performMemberLookup(discordId);

        assertFalse(result.isSuccess());
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().contains("Discord User ID not found"));
    }

    @Test
    @DisplayName("switchActiveMember - Should FAIL if user does not own the target account")
    void switchActiveMember_NotOwned_ShouldReturnFalse() {
        String discordId = "user1";
        String targetMemberId = new ObjectId().toHexString();

        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn(discordId);
        HttpSession session = mock(HttpSession.class);

        when(clanmemberRepository.findAllByDiscordId(discordId)).thenReturn(List.of());

        boolean result = clanmemberService.switchActiveMember(session, auth, targetMemberId);

        assertFalse(result, "Should not allow switching to unowned account");
        verify(session, never()).setAttribute(eq("ACTIVE_MEMBER_ID"), any());
    }

    @Test
    @DisplayName("manageActiveMemberSession - Should set Default ID if session is empty")
    void manageActiveMemberSession_EmptySession_ShouldSetDefault() {
        String discordId = "user1";
        ObjectId defaultId = new ObjectId();

        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getName()).thenReturn(discordId);

        HttpSession session = mock(HttpSession.class);
        when(session.getAttribute("ACTIVE_MEMBER_ID")).thenReturn(null);

        ClanmemberEntity member = new ClanmemberEntity();
        member.setId(defaultId);
        when(clanmemberRepository.findAllByDiscordId(discordId)).thenReturn(List.of(member));

        String result = clanmemberService.manageActiveMemberSession(session, auth);

        assertEquals(defaultId.toHexString(), result);
        verify(session).setAttribute("ACTIVE_MEMBER_ID", defaultId.toHexString());
    }

    @Test
    @DisplayName("manageActiveMemberSession - Should return null if Authentication is null or not authenticated")
    void manageActiveMemberSession_NoAuth_ShouldReturnNull() {
        HttpSession session = mock(HttpSession.class);

        String result1 = clanmemberService.manageActiveMemberSession(session, null);
        assertNull(result1);

        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(false);
        String result2 = clanmemberService.manageActiveMemberSession(session, auth);
        assertNull(result2);
    }

    @Test
    @DisplayName("switchActiveMember - Should SUCCEED if user owns the target account")
    void switchActiveMember_Owned_ShouldReturnTrue() {
        String discordId = "ownerUser";
        ObjectId targetId = new ObjectId();

        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn(discordId);
        HttpSession session = mock(HttpSession.class);

        ClanmemberEntity member = new ClanmemberEntity();
        member.setId(targetId);
        when(clanmemberRepository.findAllByDiscordId(discordId)).thenReturn(List.of(member));

        boolean result = clanmemberService.switchActiveMember(session, auth, targetId.toHexString());

        assertTrue(result);
        verify(session).setAttribute("ACTIVE_MEMBER_ID", targetId.toHexString());
    }

    @Test
    @DisplayName("updateAllClanmemberDiscordRoles - Should skip members with empty Discord IDs")
    void updateAllClanmemberDiscordRoles_EmptyId_ShouldSkip() {
        ClanmemberEntity memberWithNoId = new ClanmemberEntity();
        memberWithNoId.setDiscordId("");
        memberWithNoId.setIngameName("ManualUser");

        when(clanmemberRepository.findAllByDiscordIdIsNotNull()).thenReturn(List.of(memberWithNoId));

        clanmemberService.updateAllClanmemberDiscordRoles();

        verify(discordApiClient, never()).getDiscordMember(any());
        verify(clanmemberRepository, never()).save(any());
    }

    @Test
    @DisplayName("getMemberById - Should return member if found")
    void getMemberById_Found_ShouldReturnMember() {
        ObjectId id = new ObjectId();
        ClanmemberEntity member = new ClanmemberEntity();
        member.setId(id);

        when(clanmemberRepository.findById(id)).thenReturn(Optional.of(member));

        ClanmemberEntity result = clanmemberService.getMemberById(id.toHexString());

        assertNotNull(result);
        assertEquals(id, result.getId());
    }

    @Test
    @DisplayName("getMemberById - Should throw exception if ID is invalid format")
    void getMemberById_InvalidFormat_ShouldThrowException() {
        assertThrows(IllegalArgumentException.class, () ->
                clanmemberService.getMemberById("invalid-hex-string")
        );
    }

    @Test
    @DisplayName("getMemberById - Should throw exception if ID is null")
    void getMemberById_NullId_ShouldThrowException() {
        assertThrows(IllegalArgumentException.class, () ->
                clanmemberService.getMemberById(null)
        );
    }

    @Test
    @DisplayName("getMemberById - Should throw exception if member not found")
    void getMemberById_NotFound_ShouldThrowException() {
        ObjectId id = new ObjectId();
        when(clanmemberRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(UnlinkedAccountException.class, () ->
                clanmemberService.getMemberById(id.toHexString())
        );
    }

    @Test
    @DisplayName("getViewDataForMember - Should correctly map entity fields")
    void getViewDataForMember_ShouldMapFields() {
        String discordId = "12345";
        String avatarHash = "abc";
        String roleId = "999";
        String roleName = "Soldier";

        ClanmemberEntity member = new ClanmemberEntity();
        member.setDiscordName("TestUser");
        member.setDiscordId(discordId);
        member.setAvatarHash(avatarHash);
        member.setDiscordRoles(List.of(roleId));

        when(discordRoleService.getRoleName(roleId)).thenReturn(roleName);

        ClanmemberViewData result = clanmemberService.getViewDataForMember(member);

        assertEquals("TestUser", result.getDiscordUserName());
        assertTrue(result.getDiscordUserRoles().contains(roleName));
        assertTrue(result.getDiscordAvatarUrl().contains(discordId));
        assertTrue(result.getDiscordAvatarUrl().contains(avatarHash));
    }

    @Test
    @DisplayName("updateClanmember - Should update fields when name is unique")
    void updateClanmember_Valid_ShouldUpdate() {
        String id = new ObjectId().toHexString();
        NewClanmemberDTO dto = new NewClanmemberDTO();
        dto.setIngameName("NewName");
        dto.setClanRank(ClanRank.SOLDIER);
        dto.setClanGroup(ClanGroup.T1);

        ClanmemberEntity existingMember = new ClanmemberEntity();
        existingMember.setId(new ObjectId(id));
        existingMember.setIngameName("OldName");

        when(clanmemberRepository.findById(new ObjectId(id))).thenReturn(Optional.of(existingMember));
        when(clanmemberRepository.findByIngameName("NewName")).thenReturn(Optional.empty());

        clanmemberService.updateClanmember(id, dto, authentication);

        assertEquals("NewName", existingMember.getIngameName());
        assertEquals("SOLDIER", existingMember.getClanRank());
        assertEquals(ClanGroup.T1, existingMember.getClanGroup());

        verify(clanmemberRepository).save(existingMember);
        verify(auditLogService).logAction(eq(authentication), eq(AuditAction.MEMBER_UPDATE), any(), any());
    }

    @Test
    @DisplayName("updateClanmember - Should FAIL if name is taken by DIFFERENT user")
    void updateClanmember_NameTakenByOther_ShouldThrow() {
        String myId = new ObjectId().toHexString();
        String otherId = new ObjectId().toHexString();

        NewClanmemberDTO dto = new NewClanmemberDTO();
        dto.setIngameName("TakenName");

        ClanmemberEntity me = new ClanmemberEntity();
        me.setId(new ObjectId(myId));

        ClanmemberEntity otherUser = new ClanmemberEntity();
        otherUser.setId(new ObjectId(otherId));

        when(clanmemberRepository.findById(new ObjectId(myId))).thenReturn(Optional.of(me));

        when(clanmemberRepository.findByIngameName("TakenName")).thenReturn(Optional.of(otherUser));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                clanmemberService.updateClanmember(myId, dto, authentication)
        );

        assertEquals("The In-Game Name 'TakenName' is already in use by another member.", ex.getMessage());
    }

    @Test
    @DisplayName("updateClanmember - Should SUCCEED if name is taken by SELF (Updating own profile)")
    void updateClanmember_NameTakenBySelf_ShouldSuccess() {
        String myId = new ObjectId().toHexString();

        NewClanmemberDTO dto = new NewClanmemberDTO();
        dto.setIngameName("MyCurrentName");
        dto.setClanRank(ClanRank.SOLDIER);
        dto.setClanGroup(ClanGroup.T2);

        ClanmemberEntity me = new ClanmemberEntity();
        me.setId(new ObjectId(myId));
        me.setIngameName("MyCurrentName");

        when(clanmemberRepository.findById(new ObjectId(myId))).thenReturn(Optional.of(me));

        when(clanmemberRepository.findByIngameName("MyCurrentName")).thenReturn(Optional.of(me));

        clanmemberService.updateClanmember(myId, dto, authentication);

        verify(clanmemberRepository).save(me);
    }

    @Test
    @DisplayName("mapEntityToDto - Should map all fields correctly")
    void mapEntityToDto_ShouldMapCorrectly() {
        ClanmemberEntity entity = new ClanmemberEntity();
        entity.setDiscordId("123");
        entity.setDiscordName("User");
        entity.setPlayerNickname("Nick");
        entity.setIngameName("GameName");
        entity.setClanRank("SOLDIER");
        entity.setClanGroup(ClanGroup.T1);
        entity.setAvatarHash("hash123");
        entity.setDiscordRoles(java.util.List.of("Role1"));

        NewClanmemberDTO dto = clanmemberService.mapEntityToDto(entity);

        assertEquals("123", dto.getDiscordId());
        assertEquals("User", dto.getDiscordName());
        assertEquals("Nick", dto.getPlayerNickname());
        assertEquals("GameName", dto.getIngameName());
        assertEquals(ClanRank.SOLDIER, dto.getClanRank());
        assertEquals(ClanGroup.T1, dto.getClanGroup());
        assertEquals("hash123", dto.getAvatarHash());
        assertEquals("Role1", dto.getDiscordRoles().get(0));
    }

    @Test
    @DisplayName("updateAllClanmemberDiscordRoles - Single Account + Group Change -> SHOULD Update Group (Smart Sync)")
    void scheduledJob_SingleAccount_ShouldUpdateGroup() {
        String discordId = "12345";

        ClanmemberEntity member = new ClanmemberEntity();
        member.setDiscordId(discordId);
        member.setClanGroup(ClanGroup.T1);
        member.setDiscordRoles(java.util.List.of("OldRole"));

        when(clanmemberRepository.findAllByDiscordIdIsNotNull()).thenReturn(java.util.List.of(member));

        NewClanmemberDTO discordData = new NewClanmemberDTO();
        discordData.setDiscordRoles(java.util.List.of("T2_ROLE_ID"));
        discordData.setAvatarHash("newHash");
        when(discordApiClient.getDiscordMember(discordId)).thenReturn(Optional.of(discordData));

        when(discordRoleService.sortRoles(any())).thenReturn(java.util.List.of("T2_ROLE_ID"));

        String T2_ID = discordRoleService.getT2RoleId();
        discordData.setDiscordRoles(java.util.List.of(T2_ID));
        when(discordRoleService.sortRoles(any())).thenReturn(java.util.List.of(T2_ID));

        clanmemberService.updateAllClanmemberDiscordRoles();

        assertEquals(ClanGroup.T2, member.getClanGroup());
        verify(clanmemberRepository).save(member);
    }

    @Test
    @DisplayName("updateAllClanmemberDiscordRoles - Multi Account + Group Change -> SHOULD NOT Update Group (Safety Lock)")
    void scheduledJob_MultiAccount_ShouldNotUpdateGroup() {
        String discordId = "99999";

        ClanmemberEntity main = new ClanmemberEntity();
        main.setDiscordId(discordId);
        main.setClanGroup(ClanGroup.T1);

        ClanmemberEntity alt = new ClanmemberEntity();
        alt.setDiscordId(discordId);
        alt.setClanGroup(ClanGroup.T2);

        when(clanmemberRepository.findAllByDiscordIdIsNotNull()).thenReturn(java.util.List.of(main, alt));

        String T1_ID = discordRoleService.getT1RoleId();
        NewClanmemberDTO discordData = new NewClanmemberDTO();
        discordData.setDiscordRoles(java.util.List.of(T1_ID));
        discordData.setAvatarHash("newHash");

        when(discordApiClient.getDiscordMember(discordId)).thenReturn(Optional.of(discordData));
        when(discordRoleService.sortRoles(any())).thenReturn(java.util.List.of(T1_ID));

        clanmemberService.updateAllClanmemberDiscordRoles();

        assertEquals(ClanGroup.T2, alt.getClanGroup());

        verify(clanmemberRepository, org.mockito.Mockito.times(2)).save(any());
    }

    @Test
    @DisplayName("linkClanmember - Existing Member -> Should update lastLogin timestamp")
    void linkClanmember_ExistingMember_ShouldUpdateTimestamp() {
        String discordId = "timestamp_test_user";
        ClanmemberEntity existingMember = new ClanmemberEntity();
        existingMember.setDiscordId(discordId);
        existingMember.setLastLogin(null); // Ensure it starts null

        when(clanmemberRepository.findAllByDiscordId(discordId)).thenReturn(List.of(existingMember));
        when(discordRoleService.sortRoles(any())).thenReturn(List.of("Role1"));

        clanmemberService.linkClanmember(discordId, "User", "Hash", List.of("Role1"));

        ArgumentCaptor<ClanmemberEntity> captor = ArgumentCaptor.forClass(ClanmemberEntity.class);
        verify(clanmemberRepository).save(captor.capture());

        ClanmemberEntity savedMember = captor.getValue();
        assertNotNull(savedMember.getLastLogin(), "LastLogin timestamp should not be null");

        verify(visitorLogRepository, never()).save(any());
    }

    @Test
    @DisplayName("linkClanmember - Unknown User -> Should create NEW Visitor Log")
    void linkClanmember_UnknownUser_ShouldCreateVisitorLog() {
        String discordId = "visitor_user";
        String username = "VisitorName";
        String hash = "hash123";

        when(clanmemberRepository.findAllByDiscordId(discordId)).thenReturn(List.of());

        when(visitorLogRepository.findByDiscordId(discordId)).thenReturn(Optional.empty());

        clanmemberService.linkClanmember(discordId, username, hash, List.of());

        ArgumentCaptor<VisitorLogEntity> captor = ArgumentCaptor.forClass(VisitorLogEntity.class);
        verify(visitorLogRepository).save(captor.capture());

        VisitorLogEntity savedVisitor = captor.getValue();
        assertEquals(discordId, savedVisitor.getDiscordId());
        assertEquals(username, savedVisitor.getUsername());
        assertEquals(1, savedVisitor.getVisitCount());
        assertNotNull(savedVisitor.getLastLogin());

        verify(clanmemberRepository, never()).save(any());
    }

    @Test
    @DisplayName("linkClanmember - Returning Visitor -> Should increment Visit Count")
    void linkClanmember_ReturningVisitor_ShouldIncrementCount() {
        String discordId = "returning_visitor";

        when(clanmemberRepository.findAllByDiscordId(discordId)).thenReturn(List.of());

        VisitorLogEntity existingVisitor = new VisitorLogEntity(discordId, "OldName", "OldHash");
        existingVisitor.setId(new ObjectId());

        when(visitorLogRepository.findByDiscordId(discordId)).thenReturn(Optional.of(existingVisitor));

        clanmemberService.linkClanmember(discordId, "NewName", "NewHash", List.of());

        ArgumentCaptor<VisitorLogEntity> captor = ArgumentCaptor.forClass(VisitorLogEntity.class);
        verify(visitorLogRepository).save(captor.capture());

        VisitorLogEntity updatedVisitor = captor.getValue();
        assertEquals(2, updatedVisitor.getVisitCount());
        assertEquals("NewName", updatedVisitor.getUsername());
    }

    @Test
    @DisplayName("saveNewClanmember - Should migrate Visitor Date and Delete Log if exists")
    void saveNewClanmember_ShouldMigrateVisitorData() {
        String discordId = "promoted_user";
        LocalDateTime priorLogin = LocalDateTime.now().minusDays(2);

        NewClanmemberDTO dto = new NewClanmemberDTO();
        dto.setDiscordId(discordId);
        dto.setIngameName("PromotedPlayer");
        dto.setClanRank(ClanRank.SOLDIER);

        VisitorLogEntity existingVisitor = new VisitorLogEntity();
        existingVisitor.setDiscordId(discordId);
        existingVisitor.setLastLogin(priorLogin);

        when(visitorLogRepository.findByDiscordId(discordId)).thenReturn(Optional.of(existingVisitor));

        clanmemberService.saveNewClanmember(dto, authentication);

        ArgumentCaptor<ClanmemberEntity> memberCaptor = ArgumentCaptor.forClass(ClanmemberEntity.class);
        verify(clanmemberRepository).save(memberCaptor.capture());
        assertEquals(priorLogin, memberCaptor.getValue().getLastLogin(), "Should copy the timestamp from visitor log");

        verify(visitorLogRepository).delete(existingVisitor);
    }

    @Test
    @DisplayName("getMemberSyncStatus - Should sort by Priority: Mismatched > Synced > Unlinked")
    void getMemberSyncStatus_ShouldSortByPriority() {
        // 1. Unlinked Member (Should be last)
        ClanmemberEntity unlinked = new ClanmemberEntity();
        unlinked.setId(new ObjectId());
        unlinked.setIngameName("Z_Unlinked"); // 'Z' to prove priority beats alphabet
        unlinked.setDiscordId(null);

        // 2. Synced Member (Should be middle)
        ClanmemberEntity synced = new ClanmemberEntity();
        synced.setId(new ObjectId());
        synced.setIngameName("A_Synced");
        synced.setDiscordId("synced_id");
        synced.setAvatarHash("hash");
        synced.setPlayerNickname("Nick");

        // 3. Mismatched Member (Should be first)
        ClanmemberEntity mismatched = new ClanmemberEntity();
        mismatched.setId(new ObjectId());
        mismatched.setIngameName("M_Mismatched");
        mismatched.setDiscordId("mismatch_id");
        mismatched.setAvatarHash("old_hash"); // Mismatch!

        when(clanmemberRepository.findAll()).thenReturn(java.util.List.of(unlinked, synced, mismatched));

        // Mock API for Synced User
        NewClanmemberDTO syncedDto = new NewClanmemberDTO();
        syncedDto.setAvatarHash("hash");
        syncedDto.setPlayerNickname("Nick");
        syncedDto.setDiscordRoles(java.util.List.of());
        when(discordApiClient.getDiscordMember("synced_id")).thenReturn(Optional.of(syncedDto));

        // Mock API for Mismatched User
        NewClanmemberDTO mismatchDto = new NewClanmemberDTO();
        mismatchDto.setAvatarHash("new_hash"); // Differs from DB
        when(discordApiClient.getDiscordMember("mismatch_id")).thenReturn(Optional.of(mismatchDto));

        // Mock Roles helper
        when(discordRoleService.sortRoles(any())).thenReturn(java.util.List.of());

        // Act
        java.util.List<com.rsl.clansite.model.dto.SyncStatusDTO> result = clanmemberService.getMemberSyncStatus();

        // Assert Order
        assertEquals(3, result.size());

        // Index 0: Mismatched (Priority 1)
        assertEquals("M_Mismatched", result.get(0).getIngameName());
        assertFalse(result.get(0).isAvatarSynced());

        // Index 1: Synced (Priority 2)
        assertEquals("A_Synced", result.get(1).getIngameName());
        assertTrue(result.get(1).isAvatarSynced());

        // Index 2: Unlinked (Priority 3)
        assertEquals("Z_Unlinked", result.get(2).getIngameName());
        assertEquals("No Discord ID linked", result.get(2).getStatusMessage());
    }

    @Test
    @DisplayName("syncSingleMember - Should throw exception if member has no Discord ID")
    void syncSingleMember_NoDiscordId_ShouldThrow() {
        String id = new ObjectId().toHexString();
        ClanmemberEntity unlinked = new ClanmemberEntity();
        unlinked.setDiscordId(null);

        when(clanmemberRepository.findById(any())).thenReturn(Optional.of(unlinked));

        assertThrows(IllegalArgumentException.class, () ->
                clanmemberService.syncSingleMember(id, authentication)
        );

        verify(clanmemberRepository, never()).save(any());
    }

    @Test
    @DisplayName("syncSingleMember - Should call save if update is required")
    void syncSingleMember_ValidUpdate_ShouldSave() {
        String id = new ObjectId().toHexString();
        String discordId = "123";

        ClanmemberEntity member = new ClanmemberEntity();
        member.setId(new ObjectId(id));
        member.setDiscordId(discordId);
        member.setAvatarHash("old_hash"); // Needs update

        when(clanmemberRepository.findById(new ObjectId(id))).thenReturn(Optional.of(member));
        when(clanmemberRepository.findAllByDiscordId(discordId)).thenReturn(java.util.List.of(member)); // Single account

        // Mock API returning new data
        NewClanmemberDTO apiData = new NewClanmemberDTO();
        apiData.setAvatarHash("new_hash");
        when(discordApiClient.getDiscordMember(discordId)).thenReturn(Optional.of(apiData));

        clanmemberService.syncSingleMember(id, authentication);

        // Verify save was called via the private tryUpdateMemberRoles logic
        verify(clanmemberRepository).save(member);
        assertEquals("new_hash", member.getAvatarHash());
    }

    @Test
    @DisplayName("updateLastSeen - Should update timestamp if last login was null")
    void updateLastSeen_NullTimestamp_ShouldUpdate() {
        String discordId = "active_user";
        ClanmemberEntity member = new ClanmemberEntity();
        member.setDiscordId(discordId);
        member.setLastLogin(null); // Never logged in before

        when(clanmemberRepository.findAllByDiscordId(discordId)).thenReturn(List.of(member));

        clanmemberService.updateLastSeen(discordId);

        verify(clanmemberRepository).save(member);
        assertNotNull(member.getLastLogin());
    }

    @Test
    @DisplayName("updateLastSeen - Should update timestamp if older than 1 minute")
    void updateLastSeen_OldTimestamp_ShouldUpdate() {
        String discordId = "active_user";
        ClanmemberEntity member = new ClanmemberEntity();
        member.setDiscordId(discordId);
        member.setLastLogin(LocalDateTime.now().minusMinutes(2)); // 2 minutes ago

        when(clanmemberRepository.findAllByDiscordId(discordId)).thenReturn(List.of(member));

        clanmemberService.updateLastSeen(discordId);

        verify(clanmemberRepository).save(member);
    }

    @Test
    @DisplayName("updateLastSeen - Should IGNORE update if less than 1 minute ago (Throttling)")
    void updateLastSeen_RecentTimestamp_ShouldSkip() {
        String discordId = "spammer_user";
        ClanmemberEntity member = new ClanmemberEntity();
        member.setDiscordId(discordId);
        member.setLastLogin(LocalDateTime.now()); // Just happened

        when(clanmemberRepository.findAllByDiscordId(discordId)).thenReturn(List.of(member));

        clanmemberService.updateLastSeen(discordId);

        // Verify save was NEVER called
        verify(clanmemberRepository, never()).save(any());
    }
}