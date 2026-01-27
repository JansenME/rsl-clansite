package com.rsl.clansite.controller;

import com.rsl.clansite.model.ClanmemberViewData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.ui.Model;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

class IndexControllerIntegrationTest extends BaseControllerTest {
    private static final String LOGIN_LINK = "href=\"/login\"";

    @Test
    @DisplayName("GET / - GUEST should access homepage and SEE Login Button")
    void index_AsGuest_ShouldSucceed() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(content().string(containsString(LOGIN_LINK)));

        verify(commonsService).fillModel(any(), any());
    }

    @Test
    @DisplayName("GET /index - MEMBER should access homepage but NOT see Login Button")
    void index_AsMember_ShouldSucceed_NoLoginButton() throws Exception {
        String memberId = "member-index";

        when(clanmemberService.getFreshAuthorities(eq(memberId)))
                .thenReturn(Optional.of(Set.of(new SimpleGrantedAuthority("ROLE_MEMBER"))));

        stubFillModel("TestMember");

        mockMvc.perform(get("/index")
                        .with(oauth2User("ROLE_MEMBER", memberId)))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(content().string(not(containsString(LOGIN_LINK))));
    }

    @Test
    @DisplayName("GET /login - GUEST should see login page (200 OK)")
    void login_AsGuest_ShouldSucceed() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"));
    }

    @Test
    @DisplayName("GET /login?error=xyz - GUEST should see error message")
    void login_WithError_ShouldShowMessage() throws Exception {
        mockMvc.perform(get("/login").param("error", "Bad credentials"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"))
                .andExpect(model().attribute("loginError", "Bad credentials"));
    }

    @Test
    @DisplayName("GET /login - AUTHENTICATED user should be redirected to Profile (302)")
    void login_AsAuthenticatedUser_ShouldRedirect() throws Exception {
        String memberId = "member-login-redirect";

        // FIX: Mock authorities for Filter
        when(clanmemberService.getFreshAuthorities(eq(memberId)))
                .thenReturn(Optional.of(Set.of(new SimpleGrantedAuthority("ROLE_MEMBER"))));

        mockMvc.perform(get("/login")
                        .with(oauth2User("ROLE_MEMBER", memberId))) // Use Helper
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profile"));
    }

    protected void stubFillModel(String discordName) {
        ClanmemberViewData mockNavData = new ClanmemberViewData(
                discordName,
                List.of("ROLE_MEMBER"),
                "https://cdn.discordapp.com/avatars/123/abc.png",
                null,
                false
        );

        doAnswer(invocation -> {
            Model model = invocation.getArgument(0);
            model.addAttribute("navUserViewData", mockNavData);
            model.addAttribute("isLoggedIn", true);
            model.addAttribute("versionNumber", "1.0.0-TEST");
            model.addAttribute("currentYear", "2026");
            return null;
        }).when(commonsService).fillModel(any(Model.class), any());

        doAnswer(invocation -> {
            Model model = invocation.getArgument(0);
            model.addAttribute("navUserViewData", mockNavData);
            model.addAttribute("isLoggedIn", true);
            model.addAttribute("versionNumber", "1.0.0-TEST");
            model.addAttribute("currentYear", "2026");
            return null;
        }).when(commonsService).fillModel(any(Model.class), any(), any());
    }
}