package com.rsl.clansite.configuration;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.AbstractMongoClientConfiguration;

@Configuration
public class SpringMongoConfig extends AbstractMongoClientConfiguration {
    private static final String MONGO_URI = "mongodb://178.251.232.48:27017";
    private static final String DATABASE_NAME = "raidshadowlegends";

    @Override
    protected String getDatabaseName() {
        return DATABASE_NAME;
    }

    @Override
    public MongoClient mongoClient() {
        return MongoClients.create(MONGO_URI);
    }
}
