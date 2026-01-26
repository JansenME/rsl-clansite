package com.rsl.clansite.configuration;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.rsl.clansite.model.enums.Affinity;
import com.rsl.clansite.model.enums.Faction;
import com.rsl.clansite.model.enums.Rarity;
import com.rsl.clansite.model.enums.Type;
import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.mongodb.config.AbstractMongoClientConfiguration;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Configuration
public class SpringMongoConfig extends AbstractMongoClientConfiguration {

    @Value("${spring.data.mongodb.uri}")
    private String mongoUri;

    @Value("${spring.data.mongodb.database}")
    private String databaseName;

    @Override
    @Nonnull
    protected String getDatabaseName() {
        return databaseName;
    }

    @Override
    @Nonnull
    public MongoClient mongoClient() {
        log.info("Initializing MongoDB Client for database: {}", databaseName);
        return MongoClients.create(mongoUri);
    }

    @Bean
    @Override
    @Nonnull
    public MongoCustomConversions customConversions() {
        List<Converter<?, ?>> converters = new ArrayList<>();

        converters.add(new StringToTypeConverter());
        converters.add(new StringToRarityConverter());
        converters.add(new StringToFactionConverter());
        converters.add(new StringToAffinityConverter());

        return new MongoCustomConversions(converters);
    }

    // --- 1. TYPE Converter ---
    @ReadingConverter
    static class StringToTypeConverter implements Converter<String, Type> {
        @Override
        public Type convert(@Nonnull String source) {
            if (source.trim().isEmpty()) return null;
            try {
                return Type.valueOf(source);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    // --- 2. RARITY Converter ---
    @ReadingConverter
    static class StringToRarityConverter implements Converter<String, Rarity> {
        @Override
        public Rarity convert(@Nonnull String source) {
            if (source.trim().isEmpty()) return null;
            try {
                return Rarity.valueOf(source);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    // --- 3. FACTION Converter ---
    @ReadingConverter
    static class StringToFactionConverter implements Converter<String, Faction> {
        @Override
        public Faction convert(@Nonnull String source) {
            if (source.trim().isEmpty()) return null;
            try {
                return Faction.valueOf(source.replace(" ", "_").toUpperCase());
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    // --- 4. AFFINITY Converter ---
    @ReadingConverter
    static class StringToAffinityConverter implements Converter<String, Affinity> {
        @Override
        public Affinity convert(@Nonnull String source) {
            if (source.trim().isEmpty()) return null;
            try {
                return Affinity.valueOf(source);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }
}