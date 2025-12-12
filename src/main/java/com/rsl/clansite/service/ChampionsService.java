package com.rsl.clansite.service;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import com.rsl.clansite.exceptions.ChampionSaveException;
import com.rsl.clansite.model.Aura;
import com.rsl.clansite.model.BaseStats;
import com.rsl.clansite.model.Champion;
import com.rsl.clansite.model.dto.ChampionEntryDTO;
import com.rsl.clansite.model.entity.ChampionEntity;
import com.rsl.clansite.model.enums.Affinity;
import com.rsl.clansite.model.enums.AuraStat;
import com.rsl.clansite.model.enums.Faction;
import com.rsl.clansite.model.enums.AuraLocation;
import com.rsl.clansite.model.enums.Rarity;
import com.rsl.clansite.model.enums.Type;
import com.rsl.clansite.repository.ChampionRepository;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
public class ChampionsService {
    private final ChampionRepository championRepository;

    @Autowired
    public ChampionsService(final ChampionRepository championRepository) {
        this.championRepository = championRepository;
    }

    public List<Champion> getAllChampions() {
        return mapEntitiesToChampions(championRepository.findAll());
    }

    public List<ChampionEntity> saveAllChampionsFromCsv() {
        championRepository.deleteAll();

        List<ChampionEntity> championEntities = getChampionsFromCsv();

        championRepository.saveAll(championEntities);

        return championEntities;
    }

    public void saveNewChampion(final ChampionEntryDTO dto) throws ChampionSaveException {
        if(!StringUtils.hasText(dto.getName())) {
            throw new ChampionSaveException("Champion name cannot be empty.");
        }

        ChampionEntity entity = mapDtoToEntity(dto);

        try {
            championRepository.save(entity);
        } catch (Exception e) {
            throw new ChampionSaveException("Failed to save to database: " + e.getMessage());
        }

        appendChampionToCsv(entity);
    }

    private ChampionEntity mapDtoToEntity(final ChampionEntryDTO dto) {
        return new ChampionEntity(
                ObjectId.get(),
                dto.getName(),
                dto.getRarity(),
                dto.getType(),
                dto.getAffinity(),
                dto.getFaction(),
                getBaseStatsFromDto(dto),
                getAuraFromDTO(dto),
                dto.getArenaScore(),
                dto.getName().toLowerCase().replace(" ", "-") + ".png"
        );
    }

    private BaseStats getBaseStatsFromDto(final ChampionEntryDTO dto) {
        return new BaseStats(
                dto.getHp(),
                dto.getAttack(),
                dto.getDefense(),
                dto.getSpeed(),
                dto.getCriticalRate(),
                dto.getCriticalDamage(),
                dto.getResistance(),
                dto.getAccuracy()
        );
    }

    private Aura getAuraFromDTO(final ChampionEntryDTO dto) {
        if (!dto.isAuraExists()) {
            return null;
        }

        return new Aura(
            dto.isPercentageAura(),
            dto.getAmount(),
            dto.getStat(),
            dto.getLocation()
        );
    }

    private void appendChampionToCsv(final ChampionEntity entity) throws ChampionSaveException {
        String csvFilePath = "src/main/resources/champions.csv";

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

        try (FileWriter fw = new FileWriter(csvFilePath, true);
             BufferedWriter bw = new BufferedWriter(fw);
             PrintWriter out = new PrintWriter(bw)) {

            out.print(csvLine);

        } catch (IOException e) {
            throw new ChampionSaveException("Failed to append champion to CSV file!" + e.getMessage());
        }
    }

    private List<Champion> mapEntitiesToChampions(final List<ChampionEntity> championEntities) {
        return championEntities.stream()
                .map(this::mapEntityToChampion)
                .sorted(Comparator.comparing(Champion::getName))
                .toList();
    }

    private Champion mapEntityToChampion(final ChampionEntity championEntity) {
        return new Champion(
                championEntity.getName(),
                championEntity.getRarity(),
                championEntity.getType(),
                championEntity.getAffinity(),
                championEntity.getFaction(),
                championEntity.getBaseStats(),
                championEntity.getAura(),
                championEntity.getArenaScore(),
                championEntity.getImagename()
        );
    }

    private List<ChampionEntity> getChampionsFromCsv() {
        List<String[]> csvLines = readCsv("champions.csv");

        csvLines.remove(0);

        return mapChampionCsvToEntity(csvLines);
    }

    private List<String[]> readCsv(final String filename) {
        try (Reader reader = new BufferedReader(Files.newBufferedReader(Paths.get(ClassLoader.getSystemResource(filename).toURI())));
             CSVReader csvReader = new CSVReader(reader)) {
            return csvReader.readAll();
        } catch (IOException | CsvException | URISyntaxException e) {
            log.error(e.getMessage());
            return new ArrayList<>();
        }
    }

    private List<ChampionEntity> mapChampionCsvToEntity(final List<String[]> csvLines) {
        return csvLines.stream()
                .map(this::mapOneCsvLineToChampion)
                .toList();
    }

    private ChampionEntity mapOneCsvLineToChampion(final String[] csvLine) {
        return new ChampionEntity(
                ObjectId.get(),
                csvLine[0],
                Rarity.getRarityByName(csvLine[1]),
                Type.getTypeByName(csvLine[2]),
                Affinity.getAffinityByName(csvLine[3]),
                Faction.getFactionByName(csvLine[4]),
                getBaseStats(csvLine[5].split(",")),
                getAura(csvLine[6].split(",")),
                Double.valueOf(csvLine[7]),
                csvLine[8]
        );
    }

    private BaseStats getBaseStats(final String[] baseStatsFromCsv) {
        return new BaseStats(
                Integer.parseInt(baseStatsFromCsv[0]),
                Integer.parseInt(baseStatsFromCsv[1]),
                Integer.parseInt(baseStatsFromCsv[2]),
                Integer.parseInt(baseStatsFromCsv[3]),
                Integer.parseInt(baseStatsFromCsv[4]),
                Integer.parseInt(baseStatsFromCsv[5]),
                Integer.parseInt(baseStatsFromCsv[6]),
                Integer.parseInt(baseStatsFromCsv[7])
        );
    }

    private Aura getAura(final String[] auraFromCsv) {
        if(auraFromCsv[0].contains("null")) {
            return null;
        }

        return new Aura(
                Boolean.parseBoolean(auraFromCsv[0]),
                Integer.parseInt(auraFromCsv[1]),
                AuraStat.getAuraStatByName(auraFromCsv[2]),
                AuraLocation.getAuraLocationByName(auraFromCsv[3])
        );
    }
}
