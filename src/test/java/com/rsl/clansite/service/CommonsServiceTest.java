package com.rsl.clansite.service;

import com.rsl.clansite.model.ClanmemberViewData;
import com.rsl.clansite.model.entity.ClanmemberEntity;
import com.rsl.clansite.model.enums.QuickLink;
import com.rsl.clansite.security.SecurityService;
import jakarta.servlet.http.HttpSession;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.info.BuildProperties;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.ui.Model;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ActiveProfiles("test")
@ExtendWith(MockitoExtension.class)
class CommonsServiceTest {
    @Mock
    private ClanmemberService clanmemberService;

    @Mock
    private BuildProperties buildProperties;

    @Mock
    private Model model;

    @Mock
    private HttpSession session;

    @Mock
    private SecurityService securityService;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private CommonsService commonsService;

    @Test
    @DisplayName("fillModel should use BuildProperties for version and date")
    void fillModel_ShouldUseBuildProperties() {
        when(buildProperties.getVersion()).thenReturn("1.0.0-TEST");
        when(buildProperties.getTime()).thenReturn(Instant.parse("2025-01-01T12:00:00Z"));

        commonsService.fillModel(model, null);

        verify(model).addAttribute("versionNumber", "1.0.0-TEST");
        verify(model).addAttribute("applicationDate", "1-1-2025"); // Formatted date
        verify(model).addAttribute(eq("currentYear"), anyString());
    }

    @Test
    @DisplayName("fillModel should fallback when BuildProperties is missing (simulating null injection)")
    void fillModel_ShouldFallback_WhenPropertiesNull() {
        CommonsService serviceWithNullProps = new CommonsService(clanmemberService, null, securityService);

        serviceWithNullProps.fillModel(model, null);

        verify(model).addAttribute("versionNumber", "dev-local");
        verify(model).addAttribute("applicationDate", "Unknown Date");
    }

    @Test
    @DisplayName("fillModel should add user view data when Authenticated")
    void fillModel_ShouldAddUserData_WhenAuthenticated() {
        when(authentication.isAuthenticated()).thenReturn(true);
        ClanmemberViewData mockData = new ClanmemberViewData("User", java.util.List.of(), null, null, false);
        when(clanmemberService.getUserViewData(authentication)).thenReturn(mockData);

        commonsService.fillModel(model, authentication);

        verify(model).addAttribute("clanmemberViewData", mockData);
    }

    @Test
    @DisplayName("generateImageFilename - Standard Name - Should return slugified png")
    void generateImageFilename_Standard() {
        String result = commonsService.generateImageFilename("Kael");
        assertEquals("kael.png", result);
    }

    @Test
    @DisplayName("generateImageFilename - Name with Spaces - Should replace spaces with dashes")
    void generateImageFilename_WithSpaces() {
        String result = commonsService.generateImageFilename("Death Knight");
        assertEquals("death-knight.png", result);
    }

    @Test
    @DisplayName("generateImageFilename - Special Characters - Should remove them")
    void generateImageFilename_SpecialChars() {
        String result = commonsService.generateImageFilename("Xena: Warrior Princess");
        assertEquals("xena-warrior-princess.png", result);
    }

    @Test
    @DisplayName("generateImageFilename - Apostrophes - Should remove them")
    void generateImageFilename_Apostrophes() {
        String result = commonsService.generateImageFilename("Kael's Sword");
        assertEquals("kaels-sword.png", result);
    }

    @ParameterizedTest
    @CsvSource({
            ", placeholder.png",
            "'', placeholder.png",
            "'   ', placeholder.png"
    })
    @DisplayName("generateImageFilename - Invalid Inputs - Should return placeholder")
    void generateImageFilename_Invalid(String input, String expected) {
        String result = commonsService.generateImageFilename(input);

        if (input == null || input.isEmpty()) {
            assertEquals(expected, result);
        }
    }

    @Test
    @DisplayName("generateImageFilename - Tricky Characters - Should Clean Up")
    void generateImageFilename_Tricky() {
        String input = "Rotos the Lost Groom & Bride!";
        String result = commonsService.generateImageFilename(input);
        assertEquals("rotos-the-lost-groom-bride.png", result);
    }

    @Test
    @DisplayName("OWNER should see ALL links (Admin + Owner specific)")
    void getVisibleQuickLinks_Owner_ShouldSeeAll() {
        SimpleGrantedAuthority ownerAuth = new SimpleGrantedAuthority("ROLE_OWNER");
        List<GrantedAuthority> authList = List.of(ownerAuth);

        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);

        when(securityService.getReachableAuthorities(auth))
                .thenReturn((Collection) List.of(
                        new SimpleGrantedAuthority("ROLE_OWNER"),
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ROLE_MEMBER")
                ));

        List<CommonsService.VisibleQuickLink> links = commonsService.getVisibleQuickLinks(auth, session);

        assertTrue(links.stream().anyMatch(l -> l.label().equals(QuickLink.ADD_CHAMPION.getLabel())),
                "Owner should see Add Champion");
        assertTrue(links.stream().anyMatch(l -> l.label().equals(QuickLink.ADD_CLANMEMBER.getLabel())),
                "Owner should see Add Clanmember");
        assertTrue(links.stream().anyMatch(l -> l.label().equals(QuickLink.DATA_HEALTH.getLabel())),
                "Owner should see Data Health");
    }

    @Test
    @DisplayName("ADMIN should see Admin links but NOT Owner links")
    void getVisibleQuickLinks_Admin_ShouldSeeAdminOnly() {
        SimpleGrantedAuthority adminAuth = new SimpleGrantedAuthority("ROLE_ADMIN");
        List<GrantedAuthority> authList = List.of(adminAuth);

        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);

        when(securityService.getReachableAuthorities(auth))
                .thenReturn((Collection) authList);

        List<CommonsService.VisibleQuickLink> links = commonsService.getVisibleQuickLinks(auth, session);

        assertTrue(links.stream().anyMatch(l -> l.label().equals(QuickLink.ADD_CLANMEMBER.getLabel())),
                "Should contain Add Clanmember");
        assertTrue(links.stream().anyMatch(l -> l.label().equals(QuickLink.AUDIT_LOG.getLabel())),
                "Should contain Audit Log");

        assertFalse(links.stream().anyMatch(l -> l.label().equals(QuickLink.ADD_CHAMPION.getLabel())),
                "Admin should NOT see Add Champion");
    }

    @Test
    @DisplayName("MEMBER should see three quick links (Empty List)")
    void getVisibleQuickLinks_Member_ShouldSeeNone() {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);

        ClanmemberEntity clanmemberEntity = new ClanmemberEntity();
        clanmemberEntity.setId(new ObjectId());

        when(securityService.getReachableAuthorities(auth))
                .thenReturn((List) List.of(new SimpleGrantedAuthority("ROLE_MEMBER")));

        when(clanmemberService.getActiveClanmember(any(), any()))
                .thenReturn(clanmemberEntity);

        List<CommonsService.VisibleQuickLink> links = commonsService.getVisibleQuickLinks(auth, session);

        assertEquals(3, links.size(), "Member should see Add Siege Team, Sync My Roster and Edit My Roster quick links");
    }

    @Test
    @DisplayName("Anonymous/Null user should return empty list")
    void getVisibleQuickLinks_Anonymous_ShouldReturnEmpty() {
        List<CommonsService.VisibleQuickLink> linksNull = commonsService.getVisibleQuickLinks(null, session);
        assertTrue(linksNull.isEmpty());

        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(false);
        List<CommonsService.VisibleQuickLink> linksAnon = commonsService.getVisibleQuickLinks(auth, session);
        assertTrue(linksAnon.isEmpty());
    }
}