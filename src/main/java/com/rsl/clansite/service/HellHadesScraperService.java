package com.rsl.clansite.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rsl.clansite.exceptions.ChampionSaveException;
import com.rsl.clansite.model.dto.ChampionEntryDTO;
import com.rsl.clansite.model.enums.*;
import com.rsl.clansite.repository.ChampionRepository;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class HellHadesScraperService {

    private final ChampionsService championsService;
    private final ChampionRepository championRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String IMAGE_SAVE_DIR = "src/main/resources/static/images/champions/";

    public HellHadesScraperService(ChampionsService championsService, ChampionRepository championRepository) {
        this.championsService = championsService;
        this.championRepository = championRepository;
    }

    // --- PHASE 1: SCAN ---

    public List<ScrapeContext> scanForNewChampions(Faction faction) {
        List<ScrapeContext> newChampions = new ArrayList<>();

        if (faction.getHellHadesUrl() == null) {
            log.warn("No HellHades URL configured for faction: {}", faction.getName());
            return newChampions;
        }

        try {
            log.info("Fetching JSON from: {}", faction.getHellHadesUrl());

            List<HellHadesChampionJson> champions = fetchJson(faction.getHellHadesUrl(), new TypeReference<>() {});

            log.info("Found {} champions in JSON response.", champions.size());

            for (HellHadesChampionJson jsonChamp : champions) {
                String name = jsonChamp.getName().trim();

                if (championRepository.findByNameIgnoreCase(name).isEmpty()) {
                    String slug = name.toLowerCase()
                            .replace(" ", "-")
                            .replace("'", "")
                            .replace(".", "");

                    String detailUrl = "https://hellhades.com/raid/champions/" + slug + "/";
                    String imageUrl = "https://hellhades.com/wp-content/plugins/rsl-assets/assets/champbyIds/" + jsonChamp.getId() + ".png";

                    boolean alreadyInBatch = newChampions.stream().anyMatch(c -> c.getDetailUrl().equals(detailUrl));

                    if (!alreadyInBatch) {
                        newChampions.add(new ScrapeContext(detailUrl, imageUrl));
                    }
                }
            }

        } catch (IOException e) {
            log.error("Failed to fetch/parse faction JSON: {}", faction.getHellHadesUrl(), e);
        }

        return newChampions;
    }

    // --- PHASE 2: IMPORT ---

    public void importChampions(List<ScrapeContext> targets, Faction faction, Authentication authentication) {
        log.info("Starting bulk import of {} champions for {}", targets.size(), faction.getName());

        try {
            Files.createDirectories(Paths.get(IMAGE_SAVE_DIR));
        } catch (IOException e) {
            log.error("Could not create image directory: " + IMAGE_SAVE_DIR, e);
        }

        for (ScrapeContext context : targets) {
            try {
                scrapeSingleChampion(context, faction, authentication);
                Thread.sleep(1500);
            } catch (Exception e) {
                log.error("Failed to scrape champion from URL: {}", context.getDetailUrl(), e);
            }
        }
    }

    private void scrapeSingleChampion(ScrapeContext context, Faction faction, Authentication authentication) throws IOException, ChampionSaveException {
        Document doc = fetchDocument(context.getDetailUrl());

        String name = doc.select("h1").text().trim();
        log.info("Scraping details for: {}", name);

        if (championRepository.findByNameIgnoreCase(name).isPresent()) {
            return;
        }

        ChampionEntryDTO dto = new ChampionEntryDTO(false);
        dto.setName(name);
        dto.setFaction(faction);

        String fullPageText = doc.text();
        String fullHtml = doc.html();

        // --- PREPARE ROBUST DATA SOURCE (MOVED TO TOP) ---
        // 1. Meta Description (Most reliable source for Rarity/Type)
        String metaDescription = "";
        Element metaDescEl = doc.select("meta[name='description']").first();
        if (metaDescEl != null) {
            metaDescription = metaDescEl.attr("content");
        }

        // 2. OG Description
        Element ogDescEl = doc.select("meta[property='og:description']").first();
        String ogDescription = (ogDescEl != null) ? ogDescEl.attr("content") : "";

        // 3. Combine EVERYTHING before checking Rarity
        String combinedInfo = (doc.title() + " " + metaDescription + " " + ogDescription + " " + fullPageText).toLowerCase();

        // --- RARITY ---
        if (combinedInfo.contains("legendary")) dto.setRarity(Rarity.LEGENDARY);
        else if (combinedInfo.contains("mythical")) dto.setRarity(Rarity.MYTHICAL);
        else if (combinedInfo.contains("epic")) dto.setRarity(Rarity.EPIC);
        else if (combinedInfo.contains("rare")) dto.setRarity(Rarity.RARE);
        else if (combinedInfo.contains("uncommon")) dto.setRarity(Rarity.UNCOMMON);
        else dto.setRarity(Rarity.COMMON);

        // --- AFFINITY ---
        if (containsIgnoreCase(fullHtml, "affinity/magic.png")) dto.setAffinity(Affinity.MAGIC);
        else if (containsIgnoreCase(fullHtml, "affinity/force.png")) dto.setAffinity(Affinity.FORCE);
        else if (containsIgnoreCase(fullHtml, "affinity/spirit.png")) dto.setAffinity(Affinity.SPIRIT);
        else if (containsIgnoreCase(fullHtml, "affinity/void.png")) dto.setAffinity(Affinity.VOID);
        else {
            if (fullPageText.contains("Magic Affinity")) dto.setAffinity(Affinity.MAGIC);
            else if (fullPageText.contains("Force Affinity")) dto.setAffinity(Affinity.FORCE);
            else if (fullPageText.contains("Spirit Affinity")) dto.setAffinity(Affinity.SPIRIT);
            else if (fullPageText.contains("Void Affinity")) dto.setAffinity(Affinity.VOID);
        }

        // --- TYPE ---
        if (combinedInfo.contains("attack champion")) dto.setType(Type.ATTACK);
        else if (combinedInfo.contains("defense champion") || combinedInfo.contains("defence champion")) dto.setType(Type.DEFENSE);
        else if (combinedInfo.contains("hp champion") || combinedInfo.contains("health champion")) dto.setType(Type.HP);
        else if (combinedInfo.contains("support champion")) dto.setType(Type.SUPPORT);
        else {
            if (combinedInfo.contains(" role: attack")) dto.setType(Type.ATTACK);
            else if (combinedInfo.contains(" role: defense")) dto.setType(Type.DEFENSE);
            else if (combinedInfo.contains(" role: hp")) dto.setType(Type.HP);
            else if (combinedInfo.contains(" role: support")) dto.setType(Type.SUPPORT);
        }

        // --- DATA CHAIN: POST ID -> RATINGS -> HERO ID -> AURA ---
        String postId = extractPostId(doc);
        if (postId != null) {
            log.info("Found Post ID: {}", postId);
            fetchRatingsAndAura(postId, dto);
        } else {
            log.warn("Could not find Post ID (shortlink) for {}", name);
        }

        // Defaults
        dto.setHp(0); dto.setAttack(0); dto.setDefense(0); dto.setSpeed(0);
        dto.setCriticalRate(0); dto.setCriticalDamage(0); dto.setResistance(0); dto.setAccuracy(0);

        // --- IMAGE DOWNLOAD ---
        String targetFilename = name.toLowerCase().replace(" ", "-") + ".png";
        dto.setImagename(targetFilename);
        dto.setCurrentImageName(targetFilename);

        downloadImage(context.getImageUrl(), targetFilename);

        championsService.saveNewChampion(dto, authentication, "Imported via HellHades Scraper");
    }

    private String extractPostId(Document doc) {
        Element shortlink = doc.select("link[rel='shortlink']").first();
        if (shortlink != null) {
            String href = shortlink.attr("href");
            Matcher matcher = Pattern.compile("p=(\\d+)").matcher(href);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return null;
    }

    private void fetchRatingsAndAura(String postId, ChampionEntryDTO dto) {
        // 1. Fetch Ratings to get Arena Score AND Hero ID
        String ratingsUrl = "https://hellhades.com/wp-json/hh-api/v3/raid/ratings/" + postId;

        try {
            List<HellHadesRatingJson> ratings = fetchJson(ratingsUrl, new TypeReference<>() {});

            if (!ratings.isEmpty()) {
                HellHadesRatingJson ratingData = ratings.get(0);

                // --- ARENA SCORE ---
                // Data is 1-10 string (e.g., "9"), we convert to 1-5 float (e.g., 4.5)
                try {
                    double score = Double.parseDouble(ratingData.getArena_rating());
                    dto.setArenaScore(score / 2.0);
                    log.info("Found Ratings API. Score: {} -> {}", score, dto.getArenaScore());
                } catch (Exception e) {
                    dto.setArenaScore(0.0);
                }

                // --- AURA (Using Hero ID from Ratings) ---
                String heroId = ratingData.getHeroid();
                if (heroId != null && !heroId.isEmpty()) {
                    fetchAuraFromApi(heroId, dto);
                }
            }

        } catch (Exception e) {
            log.warn("Failed to fetch Ratings API for Post ID: {}", postId, e);
        }
    }

    private void fetchAuraFromApi(String heroId, ChampionEntryDTO dto) {
        String auraApiUrl = "https://hellhades.com/wp-json/hh-api/v3/raid/auras/" + heroId + "?mode=hero";

        try {
            List<HellHadesAuraJson> auras = fetchJson(auraApiUrl, new TypeReference<>() {});

            if (!auras.isEmpty()) {
                HellHadesAuraJson aura = auras.get(0);

                dto.setAuraExists(true);
                dto.setAmount(Integer.parseInt(aura.getStrength()));

                // Map Location
                String loc = aura.getLocation().toLowerCase();
                if (loc.contains("all")) dto.setLocation(AuraLocation.ALL_BATTLES);
                else if (loc.contains("arena")) dto.setLocation(AuraLocation.ARENA);
                else if (loc.contains("dungeon")) dto.setLocation(AuraLocation.DUNGEONS);
                else if (loc.contains("doom")) dto.setLocation(AuraLocation.DOOM_TOWER);
                else if (loc.contains("faction")) dto.setLocation(AuraLocation.FACTION_WARS);
                else dto.setLocation(AuraLocation.ALL_BATTLES);

                // Map Stat
                String type = aura.getType().toLowerCase();
                dto.setPercentageAura(true); // Default

                if (type.contains("health") || type.contains("hp")) dto.setStat(AuraStat.ALLY_HP);
                else if (type.contains("attack")) dto.setStat(AuraStat.ALLY_ATK);
                else if (type.contains("defence") || type.contains("defense")) dto.setStat(AuraStat.ALLY_DEF);
                else if (type.contains("speed")) dto.setStat(AuraStat.ALLY_SPD);
                else if (type.contains("critical") || type.contains("rate")) dto.setStat(AuraStat.ALLY_CRATE);
                else if (type.contains("resistance") || type.contains("resist")) {
                    dto.setStat(AuraStat.ALLY_RES);
                    dto.setPercentageAura(false);
                }
                else if (type.contains("accuracy")) {
                    dto.setStat(AuraStat.ALLY_ACC);
                    dto.setPercentageAura(false);
                }

                log.info("Found Aura via API: {} {} in {}", dto.getAmount(), dto.getStat(), dto.getLocation());
            }

        } catch (Exception e) {
            log.warn("No Aura found via API for Hero ID: {}", heroId);
            dto.setAuraExists(false);
        }
    }

    private boolean containsIgnoreCase(String source, String subItem) {
        return source.toLowerCase().contains(subItem.toLowerCase());
    }

    private void downloadImage(String imageUrl, String filename) {
        try {
            Connection.Response resultImageResponse = Jsoup.connect(imageUrl)
                    .userAgent("Mozilla/5.0")
                    .ignoreContentType(true)
                    .execute();

            Path targetPath = Paths.get(IMAGE_SAVE_DIR + filename);
            try (FileOutputStream out = new FileOutputStream(targetPath.toFile())) {
                out.write(resultImageResponse.bodyAsBytes());
            }
        } catch (IOException e) {
            log.error("Failed to download image for {}: {}", filename, imageUrl, e);
        }
    }

    protected Document fetchDocument(String url) throws IOException {
        return Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .get();
    }

    protected <T> T fetchJson(String url, TypeReference<T> typeReference) throws IOException {
        return objectMapper.readValue(new URL(url), typeReference);
    }

    // --- INNER CLASSES ---

    @Data
    @AllArgsConstructor
    public static class ScrapeContext {
        private String detailUrl;
        private String imageUrl;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class HellHadesChampionJson {
        private String id;
        private String name;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class HellHadesRatingJson {
        private String id;
        private String heroid;
        private String arena_rating;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class HellHadesAuraJson {
        private String strength;
        private String location;
        private String type;
    }
}