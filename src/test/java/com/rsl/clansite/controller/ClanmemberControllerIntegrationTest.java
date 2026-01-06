package com.rsl.clansite.controller;

import com.rsl.clansite.model.dto.MemberLookupResult;
import com.rsl.clansite.model.dto.NewClanmemberDTO;
import com.rsl.clansite.model.entity.ClanmemberEntity;
import com.rsl.clansite.model.enums.ClanGroup;
import com.rsl.clansite.repository.ClanmemberRepository;
import com.rsl.clansite.service.DiscordRoleService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpSession;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.session.Session;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Base64;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

class ClanmemberControllerIntegrationTest extends BaseControllerTest {
    @MockitoBean
    private ClanmemberRepository clanmemberRepository;

    @Test
    @DisplayName("GET /clanmembers - GUEST should access list but NOT see Add Button")
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

        mockMvc.perform(get("/clanmembers")
                        .with(oauth2Login()
                                .attributes(attrs -> attrs.put("sub", discordId))
                                .authorities(new SimpleGrantedAuthority("ROLE_MEMBER"))))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("linkedMembers"))
                .andExpect(content().string(not(containsString("href=\"/clanmembers/add\""))));
    }

    @Test
    @DisplayName("GET /clanmembers - COORDINATOR should access list but NOT see Add Button")
    void viewClanmembers_AsCoordinator_ShouldNotSeeAddButton() throws Exception {
        mockMvc.perform(get("/clanmembers")
                        .with(oauth2Login().authorities(new SimpleGrantedAuthority("ROLE_COORDINATOR"))))
                .andExpect(status().isOk())
                .andExpect(view().name("clanmembers"))
                .andExpect(content().string(not(containsString("href=\"/clanmembers/add\""))));
    }

    @Test
    @DisplayName("GET /clanmembers - ADMIN should see 'Add Member' button")
    void viewClanmembers_AsAdmin_ShouldSeeAddButton() throws Exception {
        mockMvc.perform(get("/clanmembers")
                        .with(oauth2Login().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(view().name("clanmembers"))
                .andExpect(content().string(containsString("href=\"/clanmembers/add\"")));
    }

    @Test
    @DisplayName("GET /clanmembers - OWNER should see 'Add Member' button (Inheritance)")
    void viewClanmembers_AsOwner_ShouldSeeAddButton() throws Exception {
        mockMvc.perform(get("/clanmembers")
                        .with(oauth2Login().authorities(new SimpleGrantedAuthority("ROLE_OWNER"))))
                .andExpect(status().isOk())
                .andExpect(view().name("clanmembers"))
                .andExpect(content().string(containsString("href=\"/clanmembers/add\"")));
    }

    @Test
    @DisplayName("POST /switch - AUTHENTICATED user should switch if they own the account")
    void switchAccount_AsOwner_ShouldSucceed() throws Exception {
        String discordId = "user1";
        ObjectId realId = new ObjectId();
        String targetMemberId = realId.toHexString();

        mockMvc.perform(post("/clanmembers/switch")
                        .with(user(discordId).roles("MEMBER"))
                        .with(csrf())
                        .param("memberId", targetMemberId))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/clanmembers"));

        verify(clanmemberService).switchActiveMember(any(HttpSession.class), any(Authentication.class), eq(targetMemberId));
    }

    @Test
    @DisplayName("POST /switch - GUEST should be redirected to Login (302)")
    void switchAccount_AsGuest_ShouldRedirect() throws Exception {
        mockMvc.perform(post("/clanmembers/switch")
                        .with(csrf())
                        .param("memberId", "anyId"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("GET /add - ADMIN should access form (200 OK)")
    void addForm_AsAdmin_ShouldSucceed() throws Exception {
        mockMvc.perform(get("/clanmembers/add")
                        .with(oauth2Login().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(view().name("clanmember-add"))
                .andExpect(model().attributeExists("clanmemberRosterDto"));
    }

    @Test
    @DisplayName("GET /add - MEMBER should be denied (Redirect to Error)")
    void addForm_AsMember_ShouldFail() throws Exception {
        mockMvc.perform(get("/clanmembers/add")
                        .with(oauth2Login().authorities(new SimpleGrantedAuthority("ROLE_MEMBER"))))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/error/403"));
    }

    @Test
    @DisplayName("GET /add - ADMIN lookup with dual roles should show warning and empty clan group")
    void addForm_WithDualRoles_ShouldShowWarning() throws Exception {
        String discordId = "dualRoleUser";

        NewClanmemberDTO mockDto = new NewClanmemberDTO();
        mockDto.setDiscordId(discordId);
        mockDto.setDiscordRoles(List.of(DiscordRoleService.T1_ROLE_ID, DiscordRoleService.T2_ROLE_ID));
        mockDto.setClanGroup(null);

        MemberLookupResult result = MemberLookupResult.success(
                mockDto,
                "Notice: This user has both T1 and T2 roles in Discord. Please manually select the correct Clan Group below."
        );

        when(clanmemberService.performMemberLookup(discordId)).thenReturn(result);

        mockMvc.perform(get("/clanmembers/add")
                        .with(oauth2Login().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
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
        mockMvc.perform(post("/clanmembers/save")
                        .with(oauth2Login().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
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
        mockMvc.perform(post("/clanmembers/save")
                        .with(oauth2Login().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .with(csrf())
                        .param("clanRank", "MEMBER"))
                .andExpect(status().isOk())
                .andExpect(view().name("clanmember-add"))
                .andExpect(model().attributeHasFieldErrors("clanmemberRosterDto", "clanRank"));
    }

    @Test
    @DisplayName("POST /save - MEMBER should be denied (Redirect to Error)")
    void saveMember_AsMember_ShouldFail() throws Exception {
        mockMvc.perform(post("/clanmembers/save")
                        .with(oauth2Login().authorities(new SimpleGrantedAuthority("ROLE_MEMBER")))
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
        String id = "someId";

        mockMvc.perform(post("/clanmembers/" + id + "/delete")
                        .with(oauth2Login().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/clanmembers"));

        verify(clanmemberService).deleteById(eq(id), any(), any());
    }

    @Test
    @DisplayName("POST /delete - MEMBER should be denied")
    void deleteMember_AsMember_ShouldFail() throws Exception {
        mockMvc.perform(post("/clanmembers/someId/delete")
                        .with(oauth2Login().authorities(new SimpleGrantedAuthority("ROLE_MEMBER")))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/error/403"));
    }

    @Test
    @DisplayName("POST /save - Should show error if In-Game Name is already in use (Custom Validator)")
    void saveClanmember_DuplicateName_ShouldShowError() throws Exception {
        when(clanmemberRepository.existsByIngameName("ExistingPlayer")).thenReturn(true);

        mockMvc.perform(post("/clanmembers/save")
                        .with(oauth2Login().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
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
        mockMvc.perform(post("/clanmembers/save")
                        .with(oauth2Login().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
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
        mockMvc.perform(post("/clanmembers/save")
                                .with(oauth2Login().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
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
        mockMvc.perform(post("/clanmembers/save")
                        .with(oauth2Login().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
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
        ClanmemberEntity member = new ClanmemberEntity();
        member.setId(new ObjectId());
        member.setIngameName("OriginalName");
        when(clanmemberService.getMemberById(anyString())).thenReturn(member);

        NewClanmemberDTO dto = new NewClanmemberDTO();
        dto.setIngameName("OriginalName");
        when(clanmemberService.mapEntityToDto(member)).thenReturn(dto);

        mockMvc.perform(get("/clanmembers/edit/" + member.getId().toHexString())
                        .with(oauth2Login().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(view().name("clanmember-edit"))
                .andExpect(model().attributeExists("clanmemberRosterDto"))
                .andExpect(model().attribute("editingMemberId", member.getId().toHexString()));
    }

    @Test
    @DisplayName("POST /edit/{id} - Valid Update should redirect to list")
    void updateClanmember_ValidData_ShouldRedirect() throws Exception {
        mockMvc.perform(post("/clanmembers/edit/12345")
                        .with(oauth2Login().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
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
        doThrow(new IllegalArgumentException("Name taken")).when(clanmemberService).updateClanmember(anyString(), any(), any());

        mockMvc.perform(post("/clanmembers/edit/12345")
                        .with(oauth2Login().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
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
        ClanmemberEntity member = new ClanmemberEntity();
        member.setId(new ObjectId());
        member.setClanGroup(ClanGroup.T1);

        when(clanmemberService.findAllClanmemberEntities()).thenReturn(List.of(member));
        when(clanmemberService.getLinkedClanmembers(any())).thenReturn(List.of());

        mockMvc.perform(get("/clanmembers")
                        .with(oauth2Login().authorities(new SimpleGrantedAuthority("ROLE_MEMBER"))))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("/clanmembers/edit/" + member.getId().toHexString()))));
    }

    @Test
    @DisplayName("GET /edit/{id} - MEMBER trying to access URL directly should be Redirected (Access Denied)")
    void editClanmemberForm_AsMember_ShouldBeForbidden() throws Exception {
        mockMvc.perform(get("/clanmembers/edit/12345")
                        .with(oauth2Login().authorities(new SimpleGrantedAuthority("ROLE_MEMBER"))))
                .andExpect(status().is3xxRedirection());
    }
}