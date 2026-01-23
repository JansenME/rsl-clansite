package com.rsl.clansite.service;

import com.rsl.clansite.client.DiscordApiClient;
import com.rsl.clansite.model.entity.SiteAssetEntity;
import com.rsl.clansite.repository.SiteAssetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ActiveProfiles("test")
@ExtendWith(MockitoExtension.class)
class SiteAssetServiceTest {
    @Mock
    private SiteAssetRepository siteAssetRepository;

    @Mock
    private DiscordApiClient discordApiClient;

    private SiteAssetService siteAssetService;

    private final String CLAN_SERVER_ID = "123456789";

    @BeforeEach
    void setUp() {
        siteAssetService = new SiteAssetService(siteAssetRepository, discordApiClient, CLAN_SERVER_ID);
    }

    @Test
    @DisplayName("getFavicon - Should return what repository returns")
    void getFavicon_ShouldReturnRepoResult() {
        SiteAssetEntity asset = new SiteAssetEntity("favicon", new byte[]{}, "hash", "image/png");
        when(siteAssetRepository.findById("favicon")).thenReturn(Optional.of(asset));

        Optional<SiteAssetEntity> result = siteAssetService.getFavicon();

        assertTrue(result.isPresent());
        assertEquals("favicon", result.get().getId());
    }

    @Test
    @DisplayName("syncFavicon - Discord returns no hash - Should do nothing")
    void syncFavicon_NoHashFromDiscord_ShouldSkip() {
        when(discordApiClient.getGuildIconHash()).thenReturn(null);

        siteAssetService.syncFavicon();

        verify(siteAssetRepository, never()).findById(anyString());
        verify(siteAssetRepository, never()).save(any());
    }

    @Test
    @DisplayName("syncFavicon - Hashes match - Should NOT download or save")
    void syncFavicon_HashesMatch_ShouldSkip() {
        String currentHash = "abc_hash";
        SiteAssetEntity existing = new SiteAssetEntity("favicon", new byte[]{1}, currentHash, "image/png");

        when(discordApiClient.getGuildIconHash()).thenReturn(currentHash);
        when(siteAssetRepository.findById("favicon")).thenReturn(Optional.of(existing));

        siteAssetService.syncFavicon();

        verify(discordApiClient, never()).downloadImage(anyString());
        verify(siteAssetRepository, never()).save(any());
    }

    @Test
    @DisplayName("syncFavicon - First Run (No DB entry) - Should Download and Save")
    void syncFavicon_FirstRun_ShouldSave() {
        String newHash = "new_hash";
        byte[] imageBytes = new byte[]{1, 2, 3};

        when(discordApiClient.getGuildIconHash()).thenReturn(newHash);
        when(siteAssetRepository.findById("favicon")).thenReturn(Optional.empty()); // DB Empty
        when(discordApiClient.downloadImage(contains(newHash))).thenReturn(imageBytes);

        siteAssetService.syncFavicon();

        verify(siteAssetRepository).save(argThat(asset ->
                asset.getId().equals("favicon") &&
                        asset.getHash().equals(newHash) &&
                        asset.getData() == imageBytes
        ));
    }

    @Test
    @DisplayName("syncFavicon - Hash Changed - Should Update Existing Entity")
    void syncFavicon_HashChanged_ShouldUpdate() {
        String oldHash = "old_hash";
        String newHash = "new_hash";
        byte[] newBytes = new byte[]{9, 9};

        SiteAssetEntity existing = new SiteAssetEntity("favicon", new byte[]{0}, oldHash, "image/png");

        when(discordApiClient.getGuildIconHash()).thenReturn(newHash);
        when(siteAssetRepository.findById("favicon")).thenReturn(Optional.of(existing));
        when(discordApiClient.downloadImage(contains(newHash))).thenReturn(newBytes);

        siteAssetService.syncFavicon();

        verify(siteAssetRepository).save(argThat(asset ->
                asset.getHash().equals(newHash) &&
                        asset.getData() == newBytes
        ));
    }

    @Test
    @DisplayName("syncFavicon - API Failure - Should Log and Continue (Not Crash)")
    void syncFavicon_ApiError_ShouldNotThrow() {
        when(discordApiClient.getGuildIconHash()).thenThrow(new RuntimeException("API Down"));

        siteAssetService.syncFavicon();

        verify(siteAssetRepository, never()).save(any());
    }
}