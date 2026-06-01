package com.chinmayshivratriwar.nl_sql_engine.config;

import com.chinmayshivratriwar.nl_sql_engine.client.CohereEmbeddingClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EmbeddingConfig {

    @Value("${cohere.api-key}")
    private String cohereApiKey;

    @Bean("schemaEmbeddingModel")
    public EmbeddingModel schemaEmbeddingModel() {
        return new CohereEmbeddingClient(cohereApiKey);
    }
}