package com.rsl.clansite.security;

import com.rsl.clansite.service.ClanmemberService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.context.ActiveProfiles;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ActiveProfiles("test")
@ExtendWith(MockitoExtension.class)
class SessionSecurityFilterTest {
    @Mock
    private ClanmemberService clanmemberService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @Mock
    private HttpSession session;

    @Mock
    private OAuth2User oauth2User;

    @InjectMocks
    private SessionSecurityFilter sessionSecurityFilter;

    @BeforeEach
    void setUp() {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        SecurityContextHolder.setContext(context);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("User Missing in DB -> Should Invalidate Session & Redirect (KICK)")
    void doFilter_WhenUserMissing_ShouldKick() throws Exception {
        String userId = "missing-user";
        setupAuthentication(userId, "ROLE_MEMBER");

        when(clanmemberService.getFreshAuthorities(userId)).thenReturn(Optional.empty());

        sessionSecurityFilter.doFilterInternal(request, response, filterChain);

        verify(session, never()).invalidate();
        verify(response, never()).sendRedirect(anyString());

        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("User Exists but No Roles -> Should Keep Session & Clear Authorities (GUEST)")
    void doFilter_WhenUserExistsButNoRoles_ShouldDowngrade() throws Exception {
        String userId = "guest-user";
        setupAuthentication(userId, "ROLE_ADMIN");

        when(clanmemberService.getFreshAuthorities(userId)).thenReturn(Optional.of(Collections.emptySet()));

        sessionSecurityFilter.doFilterInternal(request, response, filterChain);

        verify(session, never()).invalidate();
        verify(response, never()).sendRedirect(any());

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assert(auth.getAuthorities().isEmpty());

        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("User Exists with Roles -> Should Update Context")
    void doFilter_WhenUserHasNewRoles_ShouldUpdate() throws Exception {
        String userId = "active-user";
        setupAuthentication(userId, "ROLE_MEMBER");

        when(clanmemberService.getFreshAuthorities(userId))
                .thenReturn(Optional.of(Set.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        sessionSecurityFilter.doFilterInternal(request, response, filterChain);

        verify(session, never()).invalidate();

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        assert(isAdmin);

        verify(filterChain).doFilter(request, response);
    }

    private void setupAuthentication(String userId, String role) {
        when(oauth2User.getAttribute("id")).thenReturn(userId);

        OAuth2AuthenticationToken authToken = new OAuth2AuthenticationToken(
                oauth2User,
                List.of(new SimpleGrantedAuthority(role)),
                "discord"
        );
        SecurityContextHolder.getContext().setAuthentication(authToken);
    }
}