package com.rsl.clansite.controller;

import com.rsl.clansite.model.ClanmemberViewData;
import com.rsl.clansite.model.entity.ClanmemberEntity;
import com.rsl.clansite.model.enums.QuickLink;
import com.rsl.clansite.service.CommonsService;
import jakarta.servlet.http.HttpSession;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.ui.Model;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
    private static final String LINK_DATA_HEALTH = "href=\"/clanmembers/admin/data-health\"";
    private static final String LOGOUT_BUTTON = "Logout";

    @BeforeEach
    void setup() {
        doAnswer(invocation -> {
            Model model = invocation.getArgument(0);
            Authentication auth = invocation.getArgument(1);

            if (auth != null && auth.isAuthenticated()) {
                model.addAttribute("clanmemberViewData", clanmemberService.getUserViewData(auth));
                model.addAttribute("isLoggedIn", true);

                CommonsService tempService = new CommonsService(null, null);
                List<QuickLink> links = tempService.getVisibleQuickLinks(auth);
                model.addAttribute("quickLinks", links);
            } else {
                model.addAttribute("isLoggedIn", false);
            }
            return null;
        }).when(commonsService).fillModel(any(), any(), any());
    }

    @Test
    @DisplayName("GET /profile - Should redirect to /profile/{id} if session user is linked")
    void profile_RedirectsToId() throws Exception {
        String myId = "676000000000000000000001";

        when(clanmemberService.manageActiveMemberSession(any(), any())).thenReturn(myId);

        when(clanmemberService.getFreshAuthorities(eq(myId)))
                .thenReturn(Optional.of(Set.of(new SimpleGrantedAuthority("ROLE_MEMBER"))));

        mockMvc.perform(get("/profile")
                        .with(oauth2User("ROLE_MEMBER", myId)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profile/" + myId));
    }

    @Test
    @DisplayName("GET /profile/{id} - Viewing OTHER profile allowed for MEMBER")
    void viewProfile_Other_AsMember_Allowed() throws Exception {
        String targetId = "676000000000000000000002";
        ClanmemberEntity target = new ClanmemberEntity();
        target.setId(new ObjectId(targetId));

        when(clanmemberService.getMemberById(targetId)).thenReturn(target);
        when(clanmemberService.getViewDataForMember(target)).thenReturn(new ClanmemberViewData("Other", List.of(), null));

        String viewerId = "999999";

        when(clanmemberService.getFreshAuthorities(eq(viewerId)))
                .thenReturn(Optional.of(Set.of(new SimpleGrantedAuthority("ROLE_MEMBER"))));

        mockMvc.perform(get("/profile/" + targetId)
                        .with(oauth2User("ROLE_MEMBER", viewerId)))
                .andExpect(status().isOk())
                .andExpect(model().attribute("isOwnProfile", false))
                .andExpect(content().string(not(containsString(QUICK_LINKS_HEADER))))
                .andExpect(content().string(not(containsString(LOGOUT_BUTTON))));
    }

    @Test
    @DisplayName("GET /profile/{id} - Viewing OTHER profile DENIED for non-members (Redirects to 403)")
    void viewProfile_Other_AsGuest_Denied() throws Exception {
        String targetId = "676000000000000000000002";
        ClanmemberEntity target = new ClanmemberEntity();
        target.setId(new ObjectId(targetId));

        when(clanmemberService.getMemberById(targetId)).thenReturn(target);

        String guestId = "guest-123";

        when(clanmemberService.getFreshAuthorities(eq(guestId)))
                .thenReturn(Optional.of(Set.of(new SimpleGrantedAuthority("ROLE_USER"))));

        mockMvc.perform(get("/profile/" + targetId)
                        .with(oauth2User("ROLE_USER", guestId)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/error/403"));
    }

    @Test
    @DisplayName("GET /profile/{id} - OWNER should see Quick Links, Data Health, Login History, and ALL buttons")
    void viewProfile_AsOwner_ShouldSeeAll() throws Exception {
        String myId = "676000000000000000000001";
        setupMockForOwnProfile(myId, "ROLE_OWNER");

        mockMvc.perform(get("/profile/" + myId)
                        .with(oauth2User("ROLE_OWNER", myId)))
                .andExpect(status().isOk())
                .andExpect(model().attribute("isOwnProfile", true))
                .andExpect(content().string(containsString(QUICK_LINKS_HEADER)))
                .andExpect(content().string(containsString(LINK_ADD_CLANMEMBER)))
                .andExpect(content().string(containsString(LINK_AUDIT_LOG)))
                .andExpect(content().string(containsString(LINK_ADD_CHAMPION)))
                .andExpect(content().string(containsString(LINK_LOGIN_HISTORY)))
                .andExpect(content().string(containsString(LINK_DATA_HEALTH)))
                .andExpect(content().string(containsString(LOGOUT_BUTTON)));
    }

    @Test
    @DisplayName("GET /profile/{id} - ADMIN should see Quick Links, Data Health, Login History, but NOT Champion button")
    void viewProfile_AsAdmin_ShouldSeeAdminLinksOnly() throws Exception {
        String myId = "676000000000000000000001";
        setupMockForOwnProfile(myId, "ROLE_ADMIN");

        mockMvc.perform(get("/profile/" + myId)
                        .with(oauth2User("ROLE_ADMIN", myId)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(QUICK_LINKS_HEADER)))
                .andExpect(content().string(containsString(LINK_ADD_CLANMEMBER)))
                .andExpect(content().string(containsString(LINK_AUDIT_LOG)))
                .andExpect(content().string(containsString(LINK_LOGIN_HISTORY)))
                .andExpect(content().string(containsString(LINK_DATA_HEALTH)))
                .andExpect(content().string(not(containsString(LINK_ADD_CHAMPION))))
                .andExpect(content().string(containsString(LOGOUT_BUTTON)));
    }

    @Test
    @DisplayName("GET /profile/{id} - COORDINATOR should NOT see Data Health")
    void viewProfile_AsCoordinator_ShouldSeeLogoutOnly() throws Exception {
        String myId = "676000000000000000000001";
        setupMockForOwnProfile(myId, "ROLE_COORDINATOR");

        mockMvc.perform(get("/profile/" + myId)
                        .with(oauth2User("ROLE_COORDINATOR", myId)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(QUICK_LINKS_HEADER)))
                .andExpect(content().string(not(containsString(LINK_ADD_CLANMEMBER))))
                .andExpect(content().string(not(containsString(LINK_AUDIT_LOG))))
                .andExpect(content().string(not(containsString(LINK_ADD_CHAMPION))))
                .andExpect(content().string(not(containsString(LINK_LOGIN_HISTORY))))
                .andExpect(content().string(not(containsString(LINK_DATA_HEALTH))))
                .andExpect(content().string(containsString(LOGOUT_BUTTON)));
    }

    @Test
    @DisplayName("GET /profile/{id} - MEMBER should NOT see Data Health")
    void viewProfile_AsMember_ShouldSeeLogoutOnly() throws Exception {
        String myId = "676000000000000000000001";
        setupMockForOwnProfile(myId, "ROLE_MEMBER");

        mockMvc.perform(get("/profile/" + myId)
                        .with(oauth2User("ROLE_MEMBER", myId)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(QUICK_LINKS_HEADER)))
                .andExpect(content().string(containsString(LOGOUT_BUTTON)))
                .andExpect(content().string(not(containsString(LINK_ADD_CLANMEMBER))))
                .andExpect(content().string(not(containsString(LINK_AUDIT_LOG))))
                .andExpect(content().string(not(containsString(LINK_ADD_CHAMPION))))
                .andExpect(content().string(not(containsString(LINK_LOGIN_HISTORY))))
                .andExpect(content().string(not(containsString(LINK_DATA_HEALTH))));

        verify(commonsService).fillModel(any(), any(), any());
    }

    @Test
    @DisplayName("POST /profile/switch - Should call service and redirect to profile root")
    void switchAccount_ShouldCallServiceAndRedirect() throws Exception {
        String discordId = "user-switcher";
        String targetMemberId = "target-member-id";

        when(clanmemberService.getFreshAuthorities(eq(discordId)))
                .thenReturn(Optional.of(Set.of(new SimpleGrantedAuthority("ROLE_MEMBER"))));

        mockMvc.perform(post("/profile/switch")
                        .with(oauth2User("ROLE_MEMBER", discordId))
                        .with(csrf())
                        .param("memberId", targetMemberId))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profile"));

        verify(clanmemberService).switchActiveMember(any(HttpSession.class), any(Authentication.class), eq(targetMemberId));
    }

    private void setupMockForOwnProfile(String id, String... roles) {
        ClanmemberEntity me = new ClanmemberEntity();
        me.setId(new ObjectId(id));
        when(clanmemberService.getMemberById(id)).thenReturn(me);
        when(clanmemberService.getLinkedClanmembers(any())).thenReturn(List.of(me));
        when(clanmemberService.getViewDataForMember(me)).thenReturn(new ClanmemberViewData("Me", List.of(), null));

        Set<SimpleGrantedAuthority> authorities = Arrays.stream(roles)
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toSet());

        when(clanmemberService.getFreshAuthorities(eq(id))).thenReturn(Optional.of(authorities));
    }
}