package com.rsl.clansite.service;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import com.rsl.clansite.model.Aura;
import com.rsl.clansite.model.BaseStats;
import com.rsl.clansite.model.Champion;
import com.rsl.clansite.model.entity.ChampionEntity;
import com.rsl.clansite.model.enums.Affinity;
import com.rsl.clansite.model.enums.Faction;
import com.rsl.clansite.model.enums.Location;
import com.rsl.clansite.model.enums.Rarity;
import com.rsl.clansite.model.enums.Type;
import com.rsl.clansite.repository.ChampionRepository;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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
                Boolean.parseBoolean(auraFromCsv[1]),
                Integer.parseInt(auraFromCsv[2]),
                auraFromCsv[3],
                Location.getLocationByName(auraFromCsv[4])
        );
    }
}
