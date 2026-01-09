package com.rsl.clansite.service;

import com.rsl.clansite.model.ClanmemberViewData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.info.BuildProperties;
import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommonsServiceTest {
    @Mock
    private ClanmemberService clanmemberService;

    @Mock
    private BuildProperties buildProperties;

    @Mock
    private Model model;

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
        CommonsService localService = new CommonsService(clanmemberService, null);

        localService.fillModel(model, null);

        verify(model).addAttribute("versionNumber", "dev-local");
        verify(model).addAttribute("applicationDate", "Unknown Date");
    }

    @Test
    @DisplayName("fillModel should add user view data when Authenticated")
    void fillModel_ShouldAddUserData_WhenAuthenticated() {
        when(authentication.isAuthenticated()).thenReturn(true);
        ClanmemberViewData mockData = new ClanmemberViewData("User", java.util.List.of(), null);
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
}