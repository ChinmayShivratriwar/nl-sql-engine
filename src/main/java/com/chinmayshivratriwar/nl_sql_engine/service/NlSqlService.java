package com.chinmayshivratriwar.nl_sql_engine.service;

import com.chinmayshivratriwar.nl_sql_engine.llm.factory.LLMFactory;
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

    private final SchemaService schemaService;
    private final SqlExecutorService sqlExecutorService;
    private final LLMFactory llmFactory;

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
        - Always return a single SQL query only, never multiple statements separated by semicolons
        - For complex questions requiring multiple calculations, use subqueries or CTEs instead
        
        SCHEMA:
        %s
        
        QUESTION: %s
        
        SQL:
        """.formatted(schema, question);

        return llmFactory.getStrategy().generateSQL(prompt);
    }

    private String cleanSql(String sql) {
        return sql.trim()
                .replaceAll("```sql", "")
                .replaceAll("```", "")
                .trim();
    }
}