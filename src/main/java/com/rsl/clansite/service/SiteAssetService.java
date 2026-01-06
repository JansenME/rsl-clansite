package com.rsl.clansite.service;

import com.rsl.clansite.client.DiscordApiClient;
import com.rsl.clansite.model.entity.SiteAssetEntity;
import com.rsl.clansite.repository.SiteAssetRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@Slf4j
public class SiteAssetService {
    private final SiteAssetRepository siteAssetRepository;
    private final DiscordApiClient discordApiClient;
    private final String clanServerId;

    public SiteAssetService(SiteAssetRepository siteAssetRepository,
                            DiscordApiClient discordApiClient,
                            @Value("${discord.clan-server-id}") String clanServerId) {
        this.siteAssetRepository = siteAssetRepository;
        this.discordApiClient = discordApiClient;
        this.clanServerId = clanServerId;
    }

    public void syncFavicon() {
        try {
            String newHash = discordApiClient.getGuildIconHash();
            if (newHash == null) {
                log.debug("Discord returned no icon hash. Skipping favicon update.");
                return;
            }

            SiteAssetEntity existingAsset = siteAssetRepository.findById("favicon").orElse(null);

            if (existingAsset == null || !newHash.equals(existingAsset.getHash())) {
                log.info("Favicon change detected (Old: {}, New: {}). Downloading new icon...",
                        (existingAsset != null ? existingAsset.getHash() : "null"), newHash);

                String url = "https://cdn.discordapp.com/icons/" + clanServerId + "/" + newHash + ".png";
                byte[] imageBytes = discordApiClient.downloadImage(url);

                if (existingAsset == null) {
                    existingAsset = new SiteAssetEntity("favicon", imageBytes, newHash, "image/png");
                } else {
                    existingAsset.setData(imageBytes);
                    existingAsset.setHash(newHash);
                }

                siteAssetRepository.save(existingAsset);
                log.info("Favicon updated successfully.");
            }
        } catch (Exception e) {
            log.error("Failed to sync favicon: {}", e.getMessage());
        }
    }

    public Optional<SiteAssetEntity> getFavicon() {
        return siteAssetRepository.findById("favicon");
    }
}
