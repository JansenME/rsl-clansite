package com.rsl.clansite.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rsl.clansite.model.Aura;
import com.rsl.clansite.model.BaseStats;
import com.rsl.clansite.model.dto.ScrapedChampion;
import com.rsl.clansite.model.entity.ChampionEntity;
import com.rsl.clansite.model.enums.Affinity;
import com.rsl.clansite.model.enums.AuditAction;
import com.rsl.clansite.model.enums.AuraLocation;
import com.rsl.clansite.model.enums.AuraStat;
import com.rsl.clansite.model.enums.Faction;
import com.rsl.clansite.model.enums.Rarity;
import com.rsl.clansite.model.enums.Type;
import com.rsl.clansite.repository.ChampionRepository;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class HellHadesScraperService {

    private final ChampionRepository championRepository;
    private final CommonsService commonsService;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.storage.location.champion-cards}")
    private String imageStorageLocation;

    private static final double LEVEL_60_MULTIPLIER = 11.014;

    // --- MAIN FLOW: SCANNING ---

    public List<ScrapingContext> scanForChampions(Faction faction, boolean forceRefresh) {
        List<ScrapingContext> contexts = new ArrayList<>();

        if (faction.getHellHadesUrl() == null) {
            log.warn("No JSON URL configured for faction: {}", faction);
            return contexts;
        }

        try {
            // 1. JSON DISCOVERY (The "Old Way" - Robust & Correct Images)
            log.info("Fetching JSON from: {}", faction.getHellHadesUrl());
            List<HellHadesChampionJson> jsonChampions = fetchJson(faction.getHellHadesUrl(), new TypeReference<>() {});

            // 2. Build Contexts
            for (HellHadesChampionJson json : jsonChampions) {
                String name = json.getName().trim().replace("''", "'");

                // Skip if exists AND not forcing refresh
                if (!forceRefresh && championRepository.findByNameIgnoreCase(name).isPresent()) {
                    continue;
                }

                // Construct URLs using the Old Logic
                String slug = name.toLowerCase()
                        .replace("’", "")  // Curly quote
                        .replace("'", "")  // Standard quote
                        .replaceAll("[^a-z0-9]", "-") // Everything else to dash
                        .replaceAll("-+", "-")         // Collapse multiple dashes
                        .replaceAll("^-|-$", "");      // Trim dashes from ends

                String detailUrl = "https://hellhades.com/raid/champions/" + slug + "/";

                // THE MAGIC LINE: Construct the Image URL from ID
                String imageUrl = "https://hellhades.com/wp-content/plugins/rsl-assets/assets/champbyIds/" + json.getId() + ".png";

                // Add to list
                contexts.add(new ScrapingContext(name, detailUrl, imageUrl, json.getId()));
            }
            log.info("Faction {}: Found {} targets. Queueing {} for scraping.", faction, jsonChampions.size(), contexts.size());

        } catch (IOException e) {
            log.error("Failed to fetch faction JSON", e);
        }

        // 3. Parallel Scraping
        contexts.parallelStream().forEach(ctx -> {
            try {
                ScrapedChampion scraped = scrapeSingleChampion(ctx.getDetailUrl());

                // IMPORTANT: Use the image URL we constructed from the JSON ID
                scraped.setImageUrl(ctx.getImageUrl());
                // Ensure name matches exactly what we found in JSON
                scraped.setName(ctx.getName());
                // NEW: Set the baseHeroId from context
                scraped.setBaseHeroId(ctx.getHeroId());

                ctx.setScrapedData(scraped);
            } catch (Exception e) {
                log.error("Failed to scrape {}: {}", ctx.getName(), e.getMessage());
                ctx.setError(e.getMessage());
            }
        });

        return contexts;
    }

    // --- MAIN FLOW: IMPORTING (UPSERT) ---

    public void importChampions(List<ScrapingContext> contexts, Faction faction, Authentication authentication) {
        List<ChampionEntity> entitiesToSave = new ArrayList<>();

        for (ScrapingContext ctx : contexts) {
            if (ctx.getScrapedData() == null) continue;

            ScrapedChampion data = ctx.getScrapedData();
            ChampionEntity champion = championRepository.findByNameIgnoreCase(data.getName())
                    .orElse(new ChampionEntity());

            champion.setName(data.getName());
            champion.setFaction(faction);

            // NEW: Set the baseHeroId
            if (data.getBaseHeroId() != null) champion.setBaseHeroId(data.getBaseHeroId());

            if (data.getRarity() != null) champion.setRarity(data.getRarity());
            if (data.getType() != null) champion.setType(data.getType());
            if (data.getAffinity() != null) champion.setAffinity(data.getAffinity());
            if (data.getBaseStats() != null) champion.setBaseStats(data.getBaseStats());
            if (data.getAura() != null) champion.setAura(data.getAura());
            if (data.getArenaScore() != null) champion.setArenaScore(data.getArenaScore());

            // Image Filename
            String generatedFileName = commonsService.generateImageFilename(data.getName());
            champion.setImagename(generatedFileName);

            // Download Logic: Use the URL we got from the JSON ID
            if (data.getImageUrl() != null) {
                downloadImage(data.getImageUrl(), generatedFileName);
            }

            entitiesToSave.add(champion);
        }

        championRepository.saveAll(entitiesToSave);
        log.info("Imported/Updated {} champions for {}", entitiesToSave.size(), faction);

        // --- AUDIT LOGGING ---
        String details = String.format("Scrape completed. Updated/Added: %d", entitiesToSave.size());
        auditLogService.logAction(authentication, AuditAction.CHAMPION_SCRAPE, faction.name(), details);
    }

    // --- SCRAPING LOGIC ---

    private ScrapedChampion scrapeSingleChampion(String url) throws IOException {
        // Fallback for document fetching if URL is slightly off
        Document doc;
        try {
            doc = fetchDocument(url);
        } catch (IOException e) {
            // Try removing "/raid" if it fails, or vice versa (Simple retry)
            if (url.contains("/raid/")) url = url.replace("/raid/", "/");
            doc = fetchDocument(url);
        }

        ScrapedChampion dto = new ScrapedChampion();
        dto.setUrl(url);

        String name = doc.select("h1").text().trim().replace("''", "'");
        dto.setName(name);

        String postId = extractPostId(doc);
        if (postId != null) {
            fetchRatingsAuraAndStats(postId, dto);
        }

        // HTML Fallbacks for missing API data
        if (dto.getRarity() == null) {
            String raritySource = (doc.title() + " " + getMetaContent(doc, "description")).toLowerCase();
            dto.setRarity(parseRarity(raritySource));
        }

        if (dto.getType() == null) {
            String text = doc.text().toLowerCase();
            if (text.contains("attack champion")) dto.setType(Type.ATTACK);
            else if (text.contains("defense champion") || text.contains("defence champion")) dto.setType(Type.DEFENSE);
            else if (text.contains("hp champion") || text.contains("health champion")) dto.setType(Type.HP);
            else if (text.contains("support champion")) dto.setType(Type.SUPPORT);
        }

        String html = doc.html();
        if (containsIgnoreCase(html, "affinity/magic.png")) dto.setAffinity(Affinity.MAGIC);
        else if (containsIgnoreCase(html, "affinity/force.png")) dto.setAffinity(Affinity.FORCE);
        else if (containsIgnoreCase(html, "affinity/spirit.png")) dto.setAffinity(Affinity.SPIRIT);
        else if (containsIgnoreCase(html, "affinity/void.png")) dto.setAffinity(Affinity.VOID);

        return dto;
    }

    // --- HELPER METHODS ---

    protected Document fetchDocument(String url) throws IOException {
        return Jsoup.connect(url).userAgent("Mozilla/5.0").get();
    }

    protected <T> T fetchJson(String url, TypeReference<T> typeReference) throws IOException {
        return objectMapper.readValue(new URL(url), typeReference);
    }

    private String getMetaContent(Document doc, String metaName) {
        Element el = doc.select("meta[name='" + metaName + "'], meta[property='" + metaName + "']").first();
        return el != null ? el.attr("content") : "";
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

    private void fetchRatingsAuraAndStats(String postId, ScrapedChampion dto) {
        String url = "https://hellhades.com/wp-json/hh-api/v3/raid/ratings/" + postId;
        try {
            List<HellHadesRatingJson> ratings = fetchJson(url, new TypeReference<>() {});
            if (!ratings.isEmpty()) {
                HellHadesRatingJson r = ratings.get(0);
                try { dto.setArenaScore(Double.parseDouble(r.getArena_rating()) / 2.0); } catch (Exception e) { dto.setArenaScore(0.0); }
                if (r.getHeroid() != null) {
                    fetchAuraFromApi(r.getHeroid(), dto);
                    fetchBaseStatsFromApi(r.getHeroid(), dto);
                }
            }
        } catch (Exception e) { /* ignore */ }
    }

    private void fetchAuraFromApi(String heroId, ScrapedChampion dto) {
        String url = "https://hellhades.com/wp-json/hh-api/v3/raid/auras/" + heroId + "?mode=hero";
        try {
            List<HellHadesAuraJson> auras = fetchJson(url, new TypeReference<>() {});
            if (!auras.isEmpty()) {
                HellHadesAuraJson json = auras.get(0);
                Aura aura = new Aura();
                try { aura.setAmount(Integer.parseInt(json.getStrength())); } catch (Exception e) { aura.setAmount(0); }

                String loc = json.getLocation().toLowerCase();
                if (loc.contains("all")) aura.setLocation(AuraLocation.ALL_BATTLES);
                else if (loc.contains("arena")) aura.setLocation(AuraLocation.ARENA);
                else if (loc.contains("dungeon")) aura.setLocation(AuraLocation.DUNGEONS);
                else if (loc.contains("doom")) aura.setLocation(AuraLocation.DOOM_TOWER);
                else if (loc.contains("faction")) aura.setLocation(AuraLocation.FACTION_WARS);
                else aura.setLocation(AuraLocation.ALL_BATTLES);

                String type = json.getType().toLowerCase();
                aura.setPercentage(true);
                if (type.contains("health") || type.contains("hp")) aura.setStat(AuraStat.ALLY_HP);
                else if (type.contains("attack")) aura.setStat(AuraStat.ALLY_ATK);
                else if (type.contains("def")) aura.setStat(AuraStat.ALLY_DEF);
                else if (type.contains("speed")) aura.setStat(AuraStat.ALLY_SPD);
                else if (type.contains("crit")) aura.setStat(AuraStat.ALLY_CRATE);
                else if (type.contains("resist")) { aura.setStat(AuraStat.ALLY_RES); aura.setPercentage(false); }
                else if (type.contains("accuracy")) { aura.setStat(AuraStat.ALLY_ACC); aura.setPercentage(false); }
                dto.setAura(aura);
            }
        } catch (Exception e) { /* ignore */ }
    }

    private void fetchBaseStatsFromApi(String heroId, ScrapedChampion dto) {
        // Strategy 1: Try Standard Calculation (ID - 6)
        long formId = Long.parseLong(heroId) - 6;
        if (tryFetchStats(heroId, formId, dto)) return;

        // Strategy 2: If failed, try Direct ID (Fix for Commons/Pikeman)
        long directId = Long.parseLong(heroId);
        tryFetchStats(heroId, directId, dto);
    }

    private boolean tryFetchStats(String heroId, long formId, ScrapedChampion dto) {
        String url = "https://hellhades.com/wp-json/hh-api/v3/raid/forms/" + formId;
        try {
            List<HellHadesStatJson> stats = fetchJson(url, new TypeReference<>() {});
            HellHadesStatJson s = stats.stream().filter(st -> heroId.equals(st.getHeroid())).findFirst().orElse(null);

            if (s != null) {
                // Parse Logic
                if (s.getRole() != null) {
                    String r = s.getRole().toUpperCase();
                    if (r.contains("DEF") || r.contains("DEFENSE")) dto.setType(Type.DEFENSE);
                    else if (r.contains("ATTACK")) dto.setType(Type.ATTACK);
                    else if (r.contains("HP") || r.contains("HEALTH")) dto.setType(Type.HP);
                    else if (r.contains("SUPPORT")) dto.setType(Type.SUPPORT);
                }
                if (s.getRarity() != null) dto.setRarity(parseRarity(s.getRarity()));

                BaseStats bs = new BaseStats();
                double rawHp = Double.parseDouble(s.getHealth()) * LEVEL_60_MULTIPLIER;
                bs.setHp((int) (rawHp * 15));
                bs.setAttack((int) Math.round(Double.parseDouble(s.getAttack()) * LEVEL_60_MULTIPLIER));
                bs.setDefense((int) Math.round(Double.parseDouble(s.getDefense()) * LEVEL_60_MULTIPLIER));
                bs.setSpeed(Integer.parseInt(s.getSpeed()));
                bs.setResistance(Integer.parseInt(s.getResistance()));
                bs.setAccuracy(Integer.parseInt(s.getAccuracy()));
                bs.setCriticalRate((int) (Double.parseDouble(s.getCritrate()) * 100));
                bs.setCriticalDamage((int) (Double.parseDouble(s.getCritdamage()) * 100));
                dto.setBaseStats(bs);
                return true; // Success!
            }
        } catch (Exception e) { /* ignore */ }
        return false; // Failed
    }

    private boolean containsIgnoreCase(String source, String subItem) {
        return source != null && subItem != null && source.toLowerCase().contains(subItem.toLowerCase());
    }

    private void downloadImage(String imageUrl, String filename) {
        try {
            Connection.Response res = Jsoup.connect(imageUrl).userAgent("Mozilla/5.0").ignoreContentType(true).execute();
            Path directoryPath = Paths.get(imageStorageLocation);

            if (!Files.exists(directoryPath)) {
                Files.createDirectories(directoryPath);
            }

            Path filePath = directoryPath.resolve(filename);

            try (FileOutputStream out = new FileOutputStream(filePath.toFile())) {
                out.write(res.bodyAsBytes());
            }
        } catch (IOException e) {
            log.error("Image download failed for {}: {}", filename, e.getMessage());
        }
    }

    public Map<Rarity, Integer> getOnlineCounts(Faction faction) {
        Map<Rarity, Integer> counts = new HashMap<>();
        for (Rarity r : Rarity.values()) counts.put(r, 0);
        if (faction.getHellHadesUrl() == null) return counts;
        try {
            List<HellHadesChampionJson> champs = fetchJson(faction.getHellHadesUrl(), new TypeReference<>() {});
            for (HellHadesChampionJson c : champs) {
                Rarity r = parseRarity(c.getRarity());
                if (r != null) counts.put(r, counts.get(r) + 1);
            }
        } catch (IOException e) { /* ignore */ }
        return counts;
    }

    private Rarity parseRarity(String s) {
        if (s == null) return null;
        String l = s.toLowerCase();
        if (l.contains("uncommon")) return Rarity.UNCOMMON;
        if (l.contains("common")) return Rarity.COMMON;
        if (l.contains("rare")) return Rarity.RARE;
        if (l.contains("epic")) return Rarity.EPIC;
        if (l.contains("legendary")) return Rarity.LEGENDARY;
        if (l.contains("mythical")) return Rarity.MYTHICAL;
        return null;
    }

    // --- INNER CLASSES (UPDATED) ---

    @Data
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ScrapingContext {
        private String name;
        private String detailUrl;
        private String imageUrl;
        private String heroId;
        private ScrapedChampion scrapedData;
        private String error;

        // Constructor for discovery
        public ScrapingContext(String name, String detailUrl, String imageUrl, String heroId) {
            this.name = name;
            this.detailUrl = detailUrl;
            this.imageUrl = imageUrl;
            this.heroId = heroId;
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class HellHadesChampionJson {
        private String id;
        private String name;
        private String rarity;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class HellHadesRatingJson {
        private String id;
        private String heroid;
        private String arena_rating;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class HellHadesAuraJson {
        private String strength;
        private String location;
        private String type;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class HellHadesStatJson {
        private String heroid;
        private String role;
        private String rarity;
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