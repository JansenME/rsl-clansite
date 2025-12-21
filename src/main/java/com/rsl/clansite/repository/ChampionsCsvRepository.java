package com.rsl.clansite.repository;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import com.rsl.clansite.exceptions.ChampionSaveException;
import com.rsl.clansite.model.Aura;
import com.rsl.clansite.model.BaseStats;
import com.rsl.clansite.model.entity.ChampionEntity;
import com.rsl.clansite.model.enums.Affinity;
import com.rsl.clansite.model.enums.AuraLocation;
import com.rsl.clansite.model.enums.AuraStat;
import com.rsl.clansite.model.enums.Faction;
import com.rsl.clansite.model.enums.Rarity;
import com.rsl.clansite.model.enums.Type;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Repository;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Slf4j
@Repository
public class ChampionsCsvRepository {
    private static final String CSV_FILENAME = "champions.csv";
    private static final String WRITE_PATH = "src/main/resources/champions.csv";

    public List<ChampionEntity> readAllChampions() {
        try (Reader reader = new BufferedReader(Files.newBufferedReader(Paths.get(ClassLoader.getSystemResource(CSV_FILENAME).toURI())));
             CSVReader csvReader = new CSVReader(reader)) {

            List<String[]> csvLines = csvReader.readAll();
            if (!csvLines.isEmpty()) {
                csvLines.remove(0);
            }
            return mapCsvLinesToEntities(csvLines);

        } catch (IOException | CsvException | URISyntaxException e) {
            log.error("Failed to read champions CSV: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    public void appendChampion(ChampionEntity entity) throws ChampionSaveException {
        String baseStatsString = entity.getBaseStats().toCsvString();
        String auraString = entity.getAura() == null ? "null" : entity.getAura().toCsvString();

        String csvLineFormat = "\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",%.1f,\"%s\"\n";
        String csvLine = String.format(
                Locale.US,
                csvLineFormat,
                entity.getName(),
                entity.getRarity().getName(),
                entity.getType().getName(),
                entity.getAffinity().getName(),
                entity.getFaction().getName(),
                baseStatsString,
                auraString,
                entity.getArenaScore(),
                entity.getImagename()
        );

        try (FileWriter fw = new FileWriter(WRITE_PATH, true);
             BufferedWriter bw = new BufferedWriter(fw);
             PrintWriter out = new PrintWriter(bw)) {

            out.print(csvLine);

        } catch (IOException e) {
            throw new ChampionSaveException("Failed to append champion to CSV file! " + e.getMessage());
        }
    }

    private List<ChampionEntity> mapCsvLinesToEntities(List<String[]> csvLines) {
        return csvLines.stream()
                .map(this::mapOneCsvLineToEntity)
                .toList();
    }

    private ChampionEntity mapOneCsvLineToEntity(String[] csvLine) {
        return new ChampionEntity(
                ObjectId.get(),
                csvLine[0],
                Rarity.getRarityByName(csvLine[1]),
                Type.getTypeByName(csvLine[2]),
                Affinity.getAffinityByName(csvLine[3]),
                Faction.getFactionByName(csvLine[4]),
                parseBaseStats(csvLine[5].split(",")),
                parseAura(csvLine[6].split(",")),
                Double.valueOf(csvLine[7]),
                csvLine[8]
        );
    }

    private BaseStats parseBaseStats(String[] stats) {
        if (stats == null || stats.length != 8) {
            log.error("CSV Parsing Error: BaseStats field has {} fields. Expected 8. Returning default stats.",
                    stats == null ? 0 : stats.length);
            return new BaseStats();
        }
        return new BaseStats(
                Integer.parseInt(stats[0]), Integer.parseInt(stats[1]), Integer.parseInt(stats[2]),
                Integer.parseInt(stats[3]), Integer.parseInt(stats[4]), Integer.parseInt(stats[5]),
                Integer.parseInt(stats[6]), Integer.parseInt(stats[7])
        );
    }

    private Aura parseAura(String[] auraData) {
        if (auraData == null || auraData.length == 0 || "null".equalsIgnoreCase(auraData[0])) {
            return null;
        }
        if (auraData.length != 4) {
            log.error("CSV Parsing Error: Aura field has {} fields. Expected 4. Returning null.", auraData.length);
            return null;
        }
        return new Aura(
                Boolean.parseBoolean(auraData[0]),
                Integer.parseInt(auraData[1]),
                AuraStat.getAuraStatByName(auraData[2]),
                AuraLocation.getAuraLocationByName(auraData[3])
        );
    }
}
