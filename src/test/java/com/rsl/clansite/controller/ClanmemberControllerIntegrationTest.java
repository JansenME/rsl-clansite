package com.rsl.clansite.controller;

import com.rsl.clansite.model.dto.MemberLookupResult;
import com.rsl.clansite.model.dto.NewClanmemberDTO;
import com.rsl.clansite.model.entity.ClanmemberEntity;
import com.rsl.clansite.model.entity.VisitorLogEntity;
import com.rsl.clansite.model.enums.ClanGroup;
import jakarta.servlet.http.HttpSession;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

class ClanmemberControllerIntegrationTest extends BaseControllerTest {
    @BeforeEach
    void setup() {
        visitorLogRepository.deleteAll();
        clanmemberRepository.deleteAll();
    }

    @Test
    @DisplayName("GET /clanmembers - GUEST (Anonymous) should access list but NOT see Add Button")
    void viewClanmembers_AsGuest_ShouldSucceed_NoAddButton() throws Exception {
        mockMvc.perform(get("/clanmembers"))
                .andExpect(status().isOk())
                .andExpect(view().name("clanmembers"))
                .andExpect(model().attributeExists("clanmembers"))
                .andExpect(content().string(not(containsString("href=\"/clanmembers/add\""))));
    }

    @Test
    @DisplayName("GET /clanmembers - MEMBER should access list but NOT see Add Button")
    void viewClanmembers_AsMember_ShouldShowLinked_NoAddButton() throws Exception {
        String discordId = "12345";
        ClanmemberEntity entity = new ClanmemberEntity();
        entity.setId(new ObjectId());

        when(clanmemberService.getLinkedClanmembers(anyString())).thenReturn(List.of(entity));

        // FIX: Wrap in Optional.of
        when(clanmemberService.getFreshAuthorities(eq(discordId)))
                .thenReturn(Optional.of(Set.of(new SimpleGrantedAuthority("ROLE_MEMBER"))));

        mockMvc.perform(get("/clanmembers")
                        .with(oauth2User("ROLE_MEMBER", discordId)))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("linkedMembers"))
                .andExpect(content().string(not(containsString("href=\"/clanmembers/add\""))));
    }

    @Test
    @DisplayName("GET /clanmembers - COORDINATOR should access list but NOT see Add Button")
    void viewClanmembers_AsCoordinator_ShouldNotSeeAddButton() throws Exception {
        String coordId = "coord-1";

        when(clanmemberService.getFreshAuthorities(eq(coordId)))
                .thenReturn(Optional.of(Set.of(new SimpleGrantedAuthority("ROLE_COORDINATOR"))));

        mockMvc.perform(get("/clanmembers")
                        .with(oauth2User("ROLE_COORDINATOR", coordId)))
                .andExpect(status().isOk())
                .andExpect(view().name("clanmembers"))
                .andExpect(content().string(not(containsString("href=\"/clanmembers/add\""))));
    }

    @Test
    @DisplayName("GET /clanmembers - ADMIN should see 'Add Member' button")
    void viewClanmembers_AsAdmin_ShouldSeeAddButton() throws Exception {
        String adminId = "admin-1";

        when(clanmemberService.getFreshAuthorities(eq(adminId)))
                .thenReturn(Optional.of(Set.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        mockMvc.perform(get("/clanmembers")
                        .with(oauth2User("ROLE_ADMIN", adminId)))
                .andExpect(status().isOk())
                .andExpect(view().name("clanmembers"))
                .andExpect(content().string(containsString("href=\"/clanmembers/add\"")));
    }

    @Test
    @DisplayName("GET /clanmembers - OWNER should see 'Add Member' button (Inheritance)")
    void viewClanmembers_AsOwner_ShouldSeeAddButton() throws Exception {
        String ownerId = "owner-1";

        when(clanmemberService.getFreshAuthorities(eq(ownerId)))
                .thenReturn(Optional.of(Set.of(new SimpleGrantedAuthority("ROLE_OWNER"))));

        mockMvc.perform(get("/clanmembers")
                        .with(oauth2User("ROLE_OWNER", ownerId)))
                .andExpect(status().isOk())
                .andExpect(view().name("clanmembers"))
                .andExpect(content().string(containsString("href=\"/clanmembers/add\"")));
    }

    @Test
    @DisplayName("GET /add - ADMIN should access form (200 OK)")
    void addForm_AsAdmin_ShouldSucceed() throws Exception {
        String adminId = "admin-1";

        when(clanmemberService.getFreshAuthorities(eq(adminId)))
                .thenReturn(Optional.of(Set.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        mockMvc.perform(get("/clanmembers/add")
                        .with(oauth2User("ROLE_ADMIN", adminId)))
                .andExpect(status().isOk())
                .andExpect(view().name("clanmember-add"))
                .andExpect(model().attributeExists("clanmemberRosterDto"));
    }

    @Test
    @DisplayName("GET /add - MEMBER should be denied (Redirect to Error)")
    void addForm_AsMember_ShouldFail() throws Exception {
        String memberId = "member-1";

        // FIX: User exists, but is just a MEMBER
        when(clanmemberService.getFreshAuthorities(eq(memberId)))
                .thenReturn(Optional.of(Set.of(new SimpleGrantedAuthority("ROLE_MEMBER"))));

        mockMvc.perform(get("/clanmembers/add")
                        .with(oauth2User("ROLE_MEMBER", memberId)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/error/403"));
    }

    @Test
    @DisplayName("GET /add - ADMIN lookup with dual roles should show warning and empty clan group")
    void addForm_WithDualRoles_ShouldShowWarning() throws Exception {
        String discordId = "dualRoleUser";

        when(discordRoleService.getT1RoleId()).thenReturn("test-t1-id");
        when(discordRoleService.getT2RoleId()).thenReturn("test-t2-id");

        NewClanmemberDTO mockDto = new NewClanmemberDTO();
        mockDto.setDiscordId(discordId);
        mockDto.setDiscordRoles(List.of(discordRoleService.getT1RoleId(), discordRoleService.getT2RoleId()));
        mockDto.setClanGroup(null);

        MemberLookupResult result = MemberLookupResult.success(
                mockDto,
                "Notice: This user has both T1 and T2 roles in Discord. Please manually select the correct Clan Group below."
        );

        when(clanmemberService.performMemberLookup(discordId)).thenReturn(result);

        // FIX: Satisfy Filter
        when(clanmemberService.getFreshAuthorities(eq(discordId)))
                .thenReturn(Optional.of(Set.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        mockMvc.perform(get("/clanmembers/add")
                        .with(oauth2User("ROLE_ADMIN", discordId))
                        .param("discordId", discordId))
                .andExpect(status().isOk())
                .andExpect(view().name("clanmember-add"))
                .andExpect(model().attribute("lookupSuccess", true))
                .andExpect(model().attribute("lookupWarning", containsString("has both T1 and T2 roles")))
                .andExpect(model().attribute("clanmemberRosterDto", hasProperty("clanGroup", nullValue())));
    }

    @Test
    @DisplayName("POST /save - ADMIN should save valid member (302 Redirect)")
    void saveMember_AsAdmin_ShouldSucceed() throws Exception {
        String adminId = "admin-save";

        when(clanmemberService.getFreshAuthorities(eq(adminId)))
                .thenReturn(Optional.of(Set.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        mockMvc.perform(post("/clanmembers/save")
                        .with(oauth2User("ROLE_ADMIN", adminId))
                        .with(csrf())
                        .param("ingameName", "NewPlayer")
                        .param("clanRank", "SOLDIER")
                        .param("clanGroup", "T1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/clanmembers"));

        verify(clanmemberService).saveNewClanmember(any(NewClanmemberDTO.class), any());
    }

    @Test
    @DisplayName("POST /save - ADMIN with Invalid Data should see form again (200 OK)")
    void saveMember_InvalidData_ShouldReturnForm() throws Exception {
        String adminId = "admin-invalid";

        when(clanmemberService.getFreshAuthorities(eq(adminId)))
                .thenReturn(Optional.of(Set.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        mockMvc.perform(post("/clanmembers/save")
                        .with(oauth2User("ROLE_ADMIN", adminId))
                        .with(csrf())
                        .param("clanRank", "MEMBER"))
                .andExpect(status().isOk())
                .andExpect(view().name("clanmember-add"))
                .andExpect(model().attributeHasFieldErrors("clanmemberRosterDto", "clanRank"));
    }

    @Test
    @DisplayName("POST /save - MEMBER should be denied (Redirect to Error)")
    void saveMember_AsMember_ShouldFail() throws Exception {
        String memberId = "mem-1";

        when(clanmemberService.getFreshAuthorities(eq(memberId)))
                .thenReturn(Optional.of(Set.of(new SimpleGrantedAuthority("ROLE_MEMBER"))));

        mockMvc.perform(post("/clanmembers/save")
                        .with(oauth2User("ROLE_MEMBER", memberId))
                        .with(csrf())
                        .param("ingameName", "Hacker")
                        .param("clanRank", "MEMBER")
                        .param("clanGroup", "T1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/error/403"));
    }

    @Test
    @DisplayName("POST /delete - ADMIN should delete (302 Redirect)")
    void deleteMember_AsAdmin_ShouldSucceed() throws Exception {
        String adminId = "admin-del";
        String id = "someId";

        when(clanmemberService.getFreshAuthorities(eq(adminId)))
                .thenReturn(Optional.of(Set.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        mockMvc.perform(post("/clanmembers/" + id + "/delete")
                        .with(oauth2User("ROLE_ADMIN", adminId))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/clanmembers"));

        verify(clanmemberService).deleteById(eq(id), any(), any());
    }

    @Test
    @DisplayName("POST /delete - MEMBER should be denied")
    void deleteMember_AsMember_ShouldFail() throws Exception {
        String memberId = "mem-del";

        when(clanmemberService.getFreshAuthorities(eq(memberId)))
                .thenReturn(Optional.of(Set.of(new SimpleGrantedAuthority("ROLE_MEMBER"))));

        mockMvc.perform(post("/clanmembers/someId/delete")
                        .with(oauth2User("ROLE_MEMBER", memberId))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/error/403"));
    }

    @Test
    @DisplayName("POST /save - Should show error if In-Game Name is already in use (Custom Validator)")
    void saveClanmember_DuplicateName_ShouldShowError() throws Exception {
        String adminId = "admin-dup";

        when(clanmemberService.getFreshAuthorities(eq(adminId)))
                .thenReturn(Optional.of(Set.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        when(clanmemberRepository.existsByIngameName("ExistingPlayer")).thenReturn(true);

        mockMvc.perform(post("/clanmembers/save")
                        .with(oauth2User("ROLE_ADMIN", adminId))
                        .with(csrf())
                        .param("ingameName", "ExistingPlayer")
                        .param("clanRank", "SOLDIER")
                        .param("clanGroup", "T1"))
                .andExpect(status().isOk())
                .andExpect(view().name("clanmember-add"))
                .andExpect(model().attributeHasFieldErrors("clanmemberRosterDto", "ingameName"));
    }

    @Test
    @DisplayName("POST /save - Invalid Discord ID (Regex) should show error")
    void saveClanmember_InvalidDiscordId_ShouldShowError() throws Exception {
        String adminId = "admin-regex";

        when(clanmemberService.getFreshAuthorities(eq(adminId)))
                .thenReturn(Optional.of(Set.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        mockMvc.perform(post("/clanmembers/save")
                        .with(oauth2User("ROLE_ADMIN", adminId))
                        .with(csrf())
                        .param("discordId", "NotANumber")
                        .param("ingameName", "ValidName")
                        .param("clanRank", "SOLDIER")
                        .param("clanGroup", "T1"))
                .andExpect(status().isOk())
                .andExpect(view().name("clanmember-add"))
                .andExpect(model().attributeHasFieldErrors("clanmemberRosterDto", "discordId"));
    }

    @Test
    @DisplayName("POST /save - Missing Rank AND Group should show combined error message")
    void saveClanmember_MissingRankAndGroup_ShouldShowCombinedError() throws Exception {
        String adminId = "admin-missing";

        when(clanmemberService.getFreshAuthorities(eq(adminId)))
                .thenReturn(Optional.of(Set.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        mockMvc.perform(post("/clanmembers/save")
                        .with(oauth2User("ROLE_ADMIN", adminId))
                        .with(csrf())
                        .param("ingameName", "ValidName")
                )
                .andExpect(status().isOk())
                .andExpect(view().name("clanmember-add"))
                .andExpect(model().attribute("lookupError", containsString("You must select both a Clan Rank and a Clan Group.")));
    }

    @Test
    @DisplayName("POST /save - Manual Entry without In-Game Name should show error")
    void saveClanmember_ManualEntry_MissingName_ShouldShowError() throws Exception {
        String adminId = "admin-user-id";

        when(clanmemberService.getFreshAuthorities(eq(adminId)))
                .thenReturn(Optional.of(Set.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        mockMvc.perform(post("/clanmembers/save")
                        .with(oauth2User("ROLE_ADMIN", adminId))
                        .with(csrf())
                        .param("discordId", "")
                        .param("ingameName", "")
                        .param("clanRank", "SOLDIER")
                        .param("clanGroup", "T1"))
                .andExpect(status().isOk())
                .andExpect(view().name("clanmember-add"))
                .andExpect(model().attribute("lookupError", containsString("For manual entries, the In-Game Name is required")));
    }

    @Test
    @DisplayName("GET /edit/{id} - ADMIN should see edit form with pre-filled data")
    void editClanmemberForm_AsAdmin_ShouldShowForm() throws Exception {
        String adminId = "admin-edit";
        ClanmemberEntity member = new ClanmemberEntity();
        member.setId(new ObjectId());
        member.setIngameName("OriginalName");
        when(clanmemberService.getMemberById(anyString())).thenReturn(member);

        NewClanmemberDTO dto = new NewClanmemberDTO();
        dto.setIngameName("OriginalName");
        when(clanmemberService.mapEntityToDto(member)).thenReturn(dto);

        when(clanmemberService.getFreshAuthorities(eq(adminId)))
                .thenReturn(Optional.of(Set.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        mockMvc.perform(get("/clanmembers/edit/" + member.getId().toHexString())
                        .with(oauth2User("ROLE_ADMIN", adminId)))
                .andExpect(status().isOk())
                .andExpect(view().name("clanmember-edit"))
                .andExpect(model().attributeExists("clanmemberRosterDto"))
                .andExpect(model().attribute("editingMemberId", member.getId().toHexString()));
    }

    @Test
    @DisplayName("POST /edit/{id} - Valid Update should redirect to list")
    void updateClanmember_ValidData_ShouldRedirect() throws Exception {
        String adminId = "admin-upd";

        when(clanmemberService.getFreshAuthorities(eq(adminId)))
                .thenReturn(Optional.of(Set.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        mockMvc.perform(post("/clanmembers/edit/12345")
                        .with(oauth2User("ROLE_ADMIN", adminId))
                        .with(csrf())
                        .param("ingameName", "UpdatedName")
                        .param("clanRank", "SOLDIER")
                        .param("clanGroup", "T1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/clanmembers"));

        verify(clanmemberService).updateClanmember(eq("12345"), any(NewClanmemberDTO.class), any());
    }

    @Test
    @DisplayName("POST /edit/{id} - Duplicate Name (Service Exception) should reload form with error")
    void updateClanmember_DuplicateName_ShouldShowError() throws Exception {
        String adminId = "admin-fail";
        doThrow(new IllegalArgumentException("Name taken")).when(clanmemberService).updateClanmember(anyString(), any(), any());

        when(clanmemberService.getFreshAuthorities(eq(adminId)))
                .thenReturn(Optional.of(Set.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        mockMvc.perform(post("/clanmembers/edit/12345")
                        .with(oauth2User("ROLE_ADMIN", adminId))
                        .with(csrf())
                        .param("ingameName", "ExistingName")
                        .param("clanRank", "SOLDIER")
                        .param("clanGroup", "T2"))
                .andExpect(status().isOk())
                .andExpect(view().name("clanmember-edit"))
                .andExpect(model().attribute("errorMessage", "Name taken"));
    }

    @Test
    @DisplayName("GET /clanmembers - MEMBER should NOT see Edit buttons in the UI")
    void viewClanmembers_AsMember_ShouldNotShowEditButtons() throws Exception {
        String memberId = "mem-view";
        ClanmemberEntity member = new ClanmemberEntity();
        member.setId(new ObjectId());
        member.setClanGroup(ClanGroup.T1);

        when(clanmemberService.findAllClanmemberEntities()).thenReturn(List.of(member));
        when(clanmemberService.getLinkedClanmembers(any())).thenReturn(List.of());

        when(clanmemberService.getFreshAuthorities(eq(memberId)))
                .thenReturn(Optional.of(Set.of(new SimpleGrantedAuthority("ROLE_MEMBER"))));

        mockMvc.perform(get("/clanmembers")
                        .with(oauth2User("ROLE_MEMBER", memberId)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("/clanmembers/edit/" + member.getId().toHexString()))));
    }

    @Test
    @DisplayName("GET /edit/{id} - MEMBER trying to access URL directly should be Redirected (Access Denied)")
    void editClanmemberForm_AsMember_ShouldBeForbidden() throws Exception {
        String memberId = "mem-forbid";

        when(clanmemberService.getFreshAuthorities(eq(memberId)))
                .thenReturn(Optional.of(Set.of(new SimpleGrantedAuthority("ROLE_MEMBER"))));

        mockMvc.perform(get("/clanmembers/edit/12345")
                        .with(oauth2User("ROLE_MEMBER", memberId)))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("Access as ADMIN - Should return 200 and show MEMBER history (but NOT Visitor audit)")
    void getLoginHistory_AsAdmin_ShouldSucceed() throws Exception {
        String adminId = "admin_id"; // Defined explicit ID
        ClanmemberEntity member = new ClanmemberEntity();
        member.setDiscordId("mem1");
        member.setDiscordName("MemName");
        member.setIngameName("ActiveMember");
        member.setLastLogin(LocalDateTime.now());

        when(clanmemberService.findAllClanmemberEntities()).thenReturn(List.of(member));

        VisitorLogEntity visitor = new VisitorLogEntity("v1", "VisitorOne", "hash");
        visitor.setLastLogin(LocalDateTime.now());

        when(visitorLogRepository.findAll()).thenReturn(List.of(visitor));

        // Create the Mock User
        OAuth2User adminUser = createMockUser(adminId, "AdminUser", "ROLE_ADMIN");

        // FIX: Verify "admin_id" in the filter
        when(clanmemberService.getFreshAuthorities(eq(adminId)))
                .thenReturn(Optional.of(Set.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        mockMvc.perform(get("/clanmembers/admin/login-history")
                        .with(oauth2User("ROLE_ADMIN", adminId))) // Use Helper
                .andExpect(status().isOk())
                .andExpect(view().name("login-history"))
                .andExpect(content().string(containsString("Login History")))
                .andExpect(content().string(containsString("ActiveMember"))) // Should find this
                .andExpect(content().string(not(containsString("VisitorOne")))); // Should NOT find this
    }

    @Test
    @DisplayName("Access as OWNER - Should show 'Security Audit' and Visitor Data")
    void getLoginHistory_AsOwner_ShouldShowAuditTitle() throws Exception {
        String ownerId = "owner_id";

        // Prepare Visitor Data
        VisitorLogEntity visitor = new VisitorLogEntity("v1", "VisitorOne", "hash");
        visitor.setLastLogin(LocalDateTime.now());

        // FIX: Stub the Repository so the Controller gets data
        when(visitorLogRepository.findAll()).thenReturn(List.of(visitor));

        // FIX: Verify "owner_id"
        when(clanmemberService.getFreshAuthorities(eq(ownerId)))
                .thenReturn(Optional.of(Set.of(new SimpleGrantedAuthority("ROLE_OWNER"))));

        mockMvc.perform(get("/clanmembers/admin/login-history")
                        .with(oauth2User("ROLE_OWNER", ownerId))) // Use Helper
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Security Audit")))
                .andExpect(content().string(containsString("VisitorOne"))); // Owner should see this
    }

    @Test
    @DisplayName("Access as MEMBER - Should Redirect to Error Page (Not 403)")
    void getLoginHistory_AsMember_ShouldRedirectToError() throws Exception {
        String memberId = "member_id";

        // FIX: Verify "member_id" so filter passes
        when(clanmemberService.getFreshAuthorities(eq(memberId)))
                .thenReturn(Optional.of(Set.of(new SimpleGrantedAuthority("ROLE_MEMBER"))));

        mockMvc.perform(get("/clanmembers/admin/login-history")
                        .with(oauth2User("ROLE_MEMBER", memberId))) // Use Helper
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/error/403"));
    }

    @Test
    @DisplayName("Access as ANONYMOUS - Should Redirect to Login")
    void getLoginHistory_Anonymous_ShouldRedirect() throws Exception {
        mockMvc.perform(get("/clanmembers/admin/login-history"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("GET /admin/data-health - ADMIN should access dashboard (200 OK)")
    void viewDataHealth_AsAdmin_ShouldSucceed() throws Exception {
        String adminId = "admin-health";
        // Mock the service returning a dummy list
        com.rsl.clansite.model.dto.SyncStatusDTO status = new com.rsl.clansite.model.dto.SyncStatusDTO();
        status.setIngameName("TestMember");
        when(clanmemberService.getMemberSyncStatus()).thenReturn(List.of(status));

        when(clanmemberService.getFreshAuthorities(eq(adminId)))
                .thenReturn(Optional.of(Set.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        mockMvc.perform(get("/clanmembers/admin/data-health")
                        .with(oauth2User("ROLE_ADMIN", adminId)))
                .andExpect(status().isOk())
                .andExpect(view().name("discord-data-health"))
                .andExpect(model().attributeExists("statusList"))
                .andExpect(content().string(containsString("TestMember")));
    }

    @Test
    @DisplayName("GET /admin/data-health - MEMBER should be denied (Redirect to Error)")
    void viewDataHealth_AsMember_ShouldFail() throws Exception {
        String memberId = "mem-health";

        when(clanmemberService.getFreshAuthorities(eq(memberId)))
                .thenReturn(Optional.of(Set.of(new SimpleGrantedAuthority("ROLE_MEMBER"))));

        mockMvc.perform(get("/clanmembers/admin/data-health")
                        .with(oauth2User("ROLE_MEMBER", memberId)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/error/403"));
    }

    @Test
    @DisplayName("POST /admin/sync/{id} - ADMIN should sync and redirect to Dashboard")
    void syncMemberData_AsAdmin_ShouldSucceed() throws Exception {
        String adminId = "admin-sync";
        String memberId = "12345";

        when(clanmemberService.getFreshAuthorities(eq(adminId)))
                .thenReturn(Optional.of(Set.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        mockMvc.perform(post("/clanmembers/admin/sync/" + memberId)
                        .with(oauth2User("ROLE_ADMIN", adminId))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/clanmembers/admin/data-health")); // Verify URL redirect

        verify(clanmemberService).syncSingleMember(eq(memberId), any());
    }

    @Test
    @DisplayName("POST /admin/sync/{id} - Service Exception should show Error Flash Message")
    void syncMemberData_Exception_ShouldShowError() throws Exception {
        String adminId = "admin-err";
        String memberId = "bad_id";
        doThrow(new RuntimeException("API Down")).when(clanmemberService).syncSingleMember(eq(memberId), any());

        when(clanmemberService.getFreshAuthorities(eq(adminId)))
                .thenReturn(Optional.of(Set.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        mockMvc.perform(post("/clanmembers/admin/sync/" + memberId)
                        .with(oauth2User("ROLE_ADMIN", adminId))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/clanmembers/admin/data-health"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash().attributeExists("errorMessage"));
    }

    @Test
    @DisplayName("POST /admin/sync/{id} - MEMBER should be denied")
    void syncMemberData_AsMember_ShouldFail() throws Exception {
        String memberId = "member-user-id";

        when(clanmemberService.getFreshAuthorities(eq(memberId)))
                .thenReturn(Optional.of(Set.of(new SimpleGrantedAuthority("ROLE_MEMBER"))));

        mockMvc.perform(post("/clanmembers/admin/sync/123")
                        .with(oauth2User("ROLE_MEMBER", memberId))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/error/403"));
    }

    private OAuth2User createMockUser(String discordId, String username, String role) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("id", discordId);
        attributes.put("global_name", username);

        Set<GrantedAuthority> authorities = Collections.singleton(new SimpleGrantedAuthority(role));

        return new DefaultOAuth2User(
                authorities,
                attributes,
                "id"
        );
    }
}