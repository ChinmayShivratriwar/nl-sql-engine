package com.chinmayshivratriwar.nl_sql_engine.service;

import com.chinmayshivratriwar.nl_sql_engine.model.QueryRequest;
import com.chinmayshivratriwar.nl_sql_engine.model.QueryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NlSqlService {

    private final ChatClient.Builder chatClientBuilder;
    private final SchemaService schemaService;
    private final SqlExecutorService sqlExecutorService;

    @Cacheable(value = "sqlCache", key = "#request.question")
    public QueryResponse processQuery(QueryRequest request) {
        long start = System.currentTimeMillis();

        try {
            String schema = schemaService.extractSchema();
            String generatedSql = generateSql(request.getQuestion(), schema);
            String cleanSql = cleanSql(generatedSql);

            log.info("Generated SQL: {}", cleanSql);

            var results = sqlExecutorService.execute(cleanSql);
            long executionTime = System.currentTimeMillis() - start;

            return QueryResponse.builder()
                    .question(request.getQuestion())
                    .generatedSql(cleanSql)
                    .results(results)
                    .executionTimeMs(executionTime)
                    .fromCache(false)
                    .build();

        } catch (Exception e) {
            log.error("Query processing failed", e);
            return QueryResponse.builder()
                    .question(request.getQuestion())
                    .error(e.getMessage())
                    .executionTimeMs(System.currentTimeMillis() - start)
                    .build();
        }
    }

    private String generateSql(String question, String schema) {
        ChatClient chatClient = chatClientBuilder.build();

        String prompt = """
        You are a PostgreSQL expert. Generate a valid PostgreSQL query for the question below.
        
        Rules:
        - Return ONLY the SQL query, nothing else
        - No explanation, no markdown, no backticks
        - Do not add any WHERE clause unless the question explicitly asks for filtering
        - Do NOT use GROUP BY unless the question explicitly asks for aggregation, counts, sums or averages
        - If question says "for each" it means ORDER BY, not GROUP BY
        - Use ILIKE instead of = for string comparisons
        - Use ORDER BY to organize results when question says "for each"
        
        SCHEMA:
        %s
        
        QUESTION: %s
        
        SQL:
        """.formatted(schema, question);

        return chatClient.prompt()
                .user(prompt)
                .options(OllamaOptions.builder()
                        .temperature(0.0) //no creativity, it has to be deterministic
                        .numPredict(200)
                        .build())
                .call()
                .content();
    }

    private String cleanSql(String sql) {
        return sql.trim()
                .replaceAll("```sql", "")
                .replaceAll("```", "")
                .trim();
    }
}