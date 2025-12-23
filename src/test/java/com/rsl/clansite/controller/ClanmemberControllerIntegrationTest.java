package com.rsl.clansite.controller;

import com.rsl.clansite.model.dto.NewClanmemberDTO;
import com.rsl.clansite.model.entity.ClanmemberEntity;
import jakarta.servlet.http.Cookie;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.session.Session;

import java.util.Base64;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

class ClanmemberControllerIntegrationTest extends BaseControllerTest {
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

        ClanmemberEntity ownedMember = new ClanmemberEntity();
        ownedMember.setId(realId);

        when(clanmemberService.getLinkedClanmembers(any())).thenReturn(List.of(ownedMember));

        var result = mockMvc.perform(post("/clanmembers/switch")
                        .with(user(discordId).roles("MEMBER"))
                        .with(csrf())
                        .param("memberId", targetMemberId))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/clanmembers"))
                .andReturn();

        Cookie sessionCookie = result.getResponse().getCookie("SESSION");
        assertNotNull(sessionCookie, "Session Cookie should exist");

        String sessionId = new String(Base64.getDecoder().decode(sessionCookie.getValue()));
        Session storedSession = sessionRepository.findById(sessionId);

        assertNotNull(storedSession, "Session should be saved in the repository");
        assertEquals(targetMemberId, storedSession.getAttribute("ACTIVE_MEMBER_ID"));

        verify(clanmemberService).getLinkedClanmembers(eq(discordId));
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
                .andExpect(model().attributeHasFieldErrors("newClanmemberDTO", "clanRank"));
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
}