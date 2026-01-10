package com.rsl.clansite.controller;

import com.rsl.clansite.model.ClanmemberViewData;
import com.rsl.clansite.model.entity.ClanmemberEntity;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProfileControllerIntegrationTest extends BaseControllerTest {
    private static final String QUICK_LINKS_HEADER = "Quick Links";
    private static final String LINK_ADD_CLANMEMBER = "href=\"/clanmembers/add\"";
    private static final String LINK_ADD_CHAMPION = "href=\"/champions/new\"";
    private static final String LINK_AUDIT_LOG = "href=\"/audit-log\"";
    private static final String LINK_LOGIN_HISTORY = "href=\"/clanmembers/admin/login-history\"";
    private static final String LOGOUT_BUTTON = "Logout";

    @Test
    @DisplayName("GET /profile - Should redirect to /profile/{id} if session user is linked")
    void profile_RedirectsToId() throws Exception {
        String myId = "676000000000000000000001";
        when(clanmemberService.manageActiveMemberSession(any(), any())).thenReturn(myId);

        mockMvc.perform(get("/profile")
                        .with(oauth2Login()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profile/" + myId));
    }

    @Test
    @DisplayName("GET /profile/{id} - Viewing OTHER profile allowed for MEMBER, but hides Quick Links")
    void viewProfile_Other_AsMember_Allowed() throws Exception {
        String otherId = "676000000000000000000002";
        ClanmemberEntity other = new ClanmemberEntity();
        other.setId(new ObjectId(otherId));

        when(clanmemberService.getMemberById(otherId)).thenReturn(other);
        when(clanmemberService.getLinkedClanmembers(any())).thenReturn(List.of());
        when(clanmemberService.getViewDataForMember(other)).thenReturn(new ClanmemberViewData("Other", List.of(), null));

        mockMvc.perform(get("/profile/" + otherId)
                        .with(oauth2Login().authorities(new SimpleGrantedAuthority("ROLE_MEMBER"))))
                .andExpect(status().isOk())
                .andExpect(model().attribute("isOwnProfile", false))
                .andExpect(content().string(not(containsString(QUICK_LINKS_HEADER))))
                .andExpect(content().string(not(containsString(LOGOUT_BUTTON))));
    }

    @Test
    @DisplayName("GET /profile/{id} - Viewing OTHER profile DENIED for non-members (Redirects to 403)")
    void viewProfile_Other_AsGuest_Denied() throws Exception {
        String otherId = "676000000000000000000002";
        ClanmemberEntity other = new ClanmemberEntity();
        other.setId(new ObjectId(otherId));

        when(clanmemberService.getMemberById(otherId)).thenReturn(other);
        when(clanmemberService.getLinkedClanmembers(any())).thenReturn(List.of());

        mockMvc.perform(get("/profile/" + otherId)
                        .with(oauth2Login().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/error/403"));
    }

    @Test
    @DisplayName("GET /profile/{id} - OWNER should see Quick Links, Login History, and ALL buttons")
    void viewProfile_AsOwner_ShouldSeeAll() throws Exception {
        String myId = "676000000000000000000001";
        setupMockForOwnProfile(myId);

        mockMvc.perform(get("/profile/" + myId)
                        .with(oauth2Login().authorities(new SimpleGrantedAuthority("ROLE_OWNER"))))
                .andExpect(status().isOk())
                .andExpect(model().attribute("isOwnProfile", true))
                .andExpect(content().string(containsString(QUICK_LINKS_HEADER)))
                .andExpect(content().string(containsString(LINK_ADD_CLANMEMBER)))
                .andExpect(content().string(containsString(LINK_AUDIT_LOG)))
                .andExpect(content().string(containsString(LINK_ADD_CHAMPION)))
                .andExpect(content().string(containsString(LINK_LOGIN_HISTORY))) // <--- Added: Visible
                .andExpect(content().string(containsString(LOGOUT_BUTTON)));
    }

    @Test
    @DisplayName("GET /profile/{id} - ADMIN should see Quick Links, Login History, but NOT Champion button")
    void viewProfile_AsAdmin_ShouldSeeAdminLinksOnly() throws Exception {
        String myId = "676000000000000000000001";
        setupMockForOwnProfile(myId);

        mockMvc.perform(get("/profile/" + myId)
                        .with(oauth2Login().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(QUICK_LINKS_HEADER)))
                .andExpect(content().string(containsString(LINK_ADD_CLANMEMBER)))
                .andExpect(content().string(containsString(LINK_AUDIT_LOG)))
                .andExpect(content().string(containsString(LINK_LOGIN_HISTORY))) // <--- Added: Visible
                .andExpect(content().string(not(containsString(LINK_ADD_CHAMPION))))
                .andExpect(content().string(containsString(LOGOUT_BUTTON)));
    }

    @Test
    @DisplayName("GET /profile/{id} - COORDINATOR should NOT see Login History")
    void viewProfile_AsCoordinator_ShouldSeeLogoutOnly() throws Exception {
        String myId = "676000000000000000000001";
        setupMockForOwnProfile(myId);

        mockMvc.perform(get("/profile/" + myId)
                        .with(oauth2Login().authorities(new SimpleGrantedAuthority("ROLE_COORDINATOR"))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(QUICK_LINKS_HEADER)))
                .andExpect(content().string(not(containsString(LINK_ADD_CLANMEMBER))))
                .andExpect(content().string(not(containsString(LINK_AUDIT_LOG))))
                .andExpect(content().string(not(containsString(LINK_ADD_CHAMPION))))
                .andExpect(content().string(not(containsString(LINK_LOGIN_HISTORY)))) // <--- Added: Hidden
                .andExpect(content().string(containsString(LOGOUT_BUTTON)));
    }

    @Test
    @DisplayName("GET /profile/{id} - MEMBER should NOT see Login History")
    void viewProfile_AsMember_ShouldSeeLogoutOnly() throws Exception {
        String myId = "676000000000000000000001";
        setupMockForOwnProfile(myId);

        mockMvc.perform(get("/profile/" + myId)
                        .with(oauth2Login().authorities(new SimpleGrantedAuthority("ROLE_MEMBER"))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(QUICK_LINKS_HEADER)))
                .andExpect(content().string(containsString(LOGOUT_BUTTON)))
                .andExpect(content().string(not(containsString(LINK_ADD_CLANMEMBER))))
                .andExpect(content().string(not(containsString(LINK_AUDIT_LOG))))
                .andExpect(content().string(not(containsString(LINK_ADD_CHAMPION))))
                .andExpect(content().string(not(containsString(LINK_LOGIN_HISTORY)))); // <--- Added: Hidden

        verify(commonsService).fillModel(any(), any());
    }

    private void setupMockForOwnProfile(String id) {
        ClanmemberEntity me = new ClanmemberEntity();
        me.setId(new ObjectId(id));
        when(clanmemberService.getMemberById(id)).thenReturn(me);
        when(clanmemberService.getLinkedClanmembers(any())).thenReturn(List.of(me));
        when(clanmemberService.getViewDataForMember(me)).thenReturn(new ClanmemberViewData("Me", List.of(), null));
    }
}