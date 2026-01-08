package com.rsl.clansite.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rsl.clansite.exceptions.ChampionSaveException;
import com.rsl.clansite.model.dto.ChampionEntryDTO;
import com.rsl.clansite.model.enums.Affinity;
import com.rsl.clansite.model.enums.AuraLocation;
import com.rsl.clansite.model.enums.AuraStat;
import com.rsl.clansite.model.enums.Faction;
import com.rsl.clansite.model.enums.Rarity;
import com.rsl.clansite.model.enums.Type;
import com.rsl.clansite.repository.ChampionRepository;
import lombok.AllArgsConstructor;
import lombok.Data;
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

    private static final double LEVEL_60_MULTIPLIER = 11.014;

    public HellHadesScraperService(ChampionsService championsService, ChampionRepository championRepository) {
        this.championsService = championsService;
        this.championRepository = championRepository;
    }

    protected Document fetchDocument(String url) throws IOException {
        return Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .get();
    }

    protected <T> T fetchJson(String url, TypeReference<T> typeReference) throws IOException {
        return objectMapper.readValue(new URL(url), typeReference);
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
            List<HellHadesChampionJson> champions = fetchJson(
                    faction.getHellHadesUrl(),
                    new TypeReference<>() {}
            );

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

        // 1. Meta Description (High confidence)
        String metaDescription = "";
        Element metaDescEl = doc.select("meta[name='description']").first();
        if (metaDescEl != null) metaDescription = metaDescEl.attr("content");

        // 2. OG Description
        Element ogDescEl = doc.select("meta[property='og:description']").first();
        String ogDescription = (ogDescEl != null) ? ogDescEl.attr("content") : "";

        // --- RARITY CHECK (Low Noise) ---
        // We ONLY use the Title and Meta tags here. We DO NOT include fullPageText.
        String raritySource = (doc.title() + " " + metaDescription + " " + ogDescription).toLowerCase();

        // Initialize as NULL so we can detect missing data later
        dto.setRarity(null);

        if (raritySource.contains("mythical")) dto.setRarity(Rarity.MYTHICAL);
        else if (raritySource.contains("legendary")) dto.setRarity(Rarity.LEGENDARY);
        else if (raritySource.contains("epic")) dto.setRarity(Rarity.EPIC);
        else if (raritySource.contains("uncommon")) dto.setRarity(Rarity.UNCOMMON); // Check Uncommon BEFORE Rare/Common
        else if (raritySource.contains("rare")) dto.setRarity(Rarity.RARE);
        else if (raritySource.contains("common")) dto.setRarity(Rarity.COMMON);

        // --- TYPE & AFFINITY CHECK (High Context) ---
        // For Type, we still want the full text because "Support Champion" might be mentioned further down.
        String fullInfo = (raritySource + " " + fullPageText).toLowerCase();

        // Affinity
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

        // Type
        if (fullInfo.contains("attack champion")) dto.setType(Type.ATTACK);
        else if (fullInfo.contains("defense champion") || fullInfo.contains("defence champion")) dto.setType(Type.DEFENSE);
        else if (fullInfo.contains("hp champion") || fullInfo.contains("health champion")) dto.setType(Type.HP);
        else if (fullInfo.contains("support champion")) dto.setType(Type.SUPPORT);
        else {
            if (fullInfo.contains(" role: attack")) dto.setType(Type.ATTACK);
            else if (fullInfo.contains(" role: defense")) dto.setType(Type.DEFENSE);
            else if (fullInfo.contains(" role: hp")) dto.setType(Type.HP);
            else if (fullInfo.contains(" role: support")) dto.setType(Type.SUPPORT);
        }

        // --- DATA CHAIN ---
        String postId = extractPostId(doc);
        if (postId != null) {
            log.info("Found Post ID: {}", postId);
            fetchRatingsAuraAndStats(postId, dto);
        } else {
            log.warn("Could not find Post ID (shortlink) for {}", name);
        }

        // Defaults
        // If we found stats via API, these will be overwritten. If not, they remain 0 (safe default).
        if (dto.getHp() == 0) {
            dto.setHp(0); dto.setAttack(0); dto.setDefense(0); dto.setSpeed(0);
            dto.setCriticalRate(0); dto.setCriticalDamage(0); dto.setResistance(0); dto.setAccuracy(0);
        }

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
            if (matcher.find()) return matcher.group(1);
        }
        return null;
    }

    private void fetchRatingsAuraAndStats(String postId, ChampionEntryDTO dto) {
        // 1. Fetch Ratings -> Gets Arena Score AND Hero ID
        String ratingsUrl = "https://hellhades.com/wp-json/hh-api/v3/raid/ratings/" + postId;

        try {
            List<HellHadesRatingJson> ratings = fetchJson(ratingsUrl, new TypeReference<List<HellHadesRatingJson>>() {});

            if (!ratings.isEmpty()) {
                HellHadesRatingJson ratingData = ratings.get(0);

                // Arena Score
                try {
                    double score = Double.parseDouble(ratingData.getArena_rating());
                    dto.setArenaScore(score / 2.0);
                } catch (Exception e) {
                    dto.setArenaScore(0.0);
                }

                // Get Hero ID to fetch Aura and Stats
                String heroId = ratingData.getHeroid();
                if (heroId != null && !heroId.isEmpty()) {
                    fetchAuraFromApi(heroId, dto);
                    fetchBaseStatsFromApi(heroId, dto);
                }
            }

        } catch (Exception e) {
            log.warn("Failed to fetch Ratings API for Post ID: {}", postId, e);
        }
    }

    private void fetchAuraFromApi(String heroId, ChampionEntryDTO dto) {
        String auraApiUrl = "https://hellhades.com/wp-json/hh-api/v3/raid/auras/" + heroId + "?mode=hero";

        try {
            List<HellHadesAuraJson> auras = fetchJson(auraApiUrl, new TypeReference<List<HellHadesAuraJson>>() {});

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
                dto.setPercentageAura(true);

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
            }
        } catch (Exception e) {
            log.warn("No Aura found via API for Hero ID: {}", heroId);
            dto.setAuraExists(false);
        }
    }

    private void fetchBaseStatsFromApi(String heroId, ChampionEntryDTO dto) {
        // LOGIC: The API requires the "Base Form ID" (0 Ascension), but we have the "Max Ascended ID" (6 Ascension).
        // Since all champions have 6 levels of ascension, the offset is always 6.
        long formId = Long.parseLong(heroId) - 6;

        String statsApiUrl = "https://hellhades.com/wp-json/hh-api/v3/raid/forms/" + formId;
        log.info("DEBUG: Calling Stats API: {}", statsApiUrl);

        try {
            List<HellHadesStatJson> statsList = fetchJson(statsApiUrl, new TypeReference<List<HellHadesStatJson>>() {});

            // Find the entry that matches our specific heroId (Fully Ascended)
            // matching "8686" inside the list returned by "8680"
            HellHadesStatJson stat = statsList.stream()
                    .filter(s -> heroId.equals(s.getHeroid()))
                    .findFirst()
                    .orElse(null);

            if (stat != null) {
                // HP LOGIC: Truncate (Floor) the base scaling first, then multiply by 15
                double rawHpBase = Double.parseDouble(stat.getHealth()) * LEVEL_60_MULTIPLIER;
                dto.setHp((int) rawHpBase * 15);

                // ATK/DEF LOGIC: Standard Rounding
                dto.setAttack((int) Math.round(Double.parseDouble(stat.getAttack()) * LEVEL_60_MULTIPLIER));
                dto.setDefense((int) Math.round(Double.parseDouble(stat.getDefense()) * LEVEL_60_MULTIPLIER));

                // Direct parsing for the rest
                dto.setSpeed(Integer.parseInt(stat.getSpeed()));
                dto.setResistance(Integer.parseInt(stat.getResistance()));
                dto.setAccuracy(Integer.parseInt(stat.getAccuracy()));

                // Percentages (0.15 -> 15)
                dto.setCriticalRate((int) (Double.parseDouble(stat.getCritrate()) * 100));
                dto.setCriticalDamage((int) (Double.parseDouble(stat.getCritdamage()) * 100));

                log.info("Calculated Stats: HP={} ATK={} DEF={}", dto.getHp(), dto.getAttack(), dto.getDefense());
            } else {
                log.warn("Stats API returned data, but could not find matching Hero ID: {}", heroId);
            }

        } catch (Exception e) {
            log.warn("Failed to fetch/parse Stats for Form ID: {} (Hero ID: {})", formId, heroId, e);
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
    @lombok.NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class HellHadesRatingJson {
        private String id;
        private String heroid;
        private String arena_rating;
    }

    @Data
    @lombok.NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class HellHadesAuraJson {
        private String strength;
        private String location;
        private String type;
    }

    @Data
    @lombok.NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class HellHadesStatJson {
        private String heroid;
        private String health;
        private String attack;
        private String defense;
        private String speed;
        private String critrate;
        private String critdamage;
        private String resistance;
        private String accuracy;
    }
}