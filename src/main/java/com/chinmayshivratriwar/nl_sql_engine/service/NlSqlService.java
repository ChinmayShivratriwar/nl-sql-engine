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

import java.util.Arrays;
import java.util.stream.Collectors;

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
            cleanSql = fixGroupBy(cleanSql);

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
        - CRITICAL: If SELECT contains SUM(), COUNT(), AVG(), MAX() or MIN(), every non-aggregated column in SELECT MUST appear in GROUP BY. No exceptions.
        - When filtering text with ILIKE, always use wildcard patterns like ILIKE '%%value%%' unless the question asks for exact match
        
        EXAMPLES:
        
        Question: Show total transactions by employee
        CORRECT SQL: SELECT e.name, SUM(t.amount) AS total FROM employees e JOIN transactions t ON e.id = t.employee_id GROUP BY e.name
        WRONG SQL: SELECT e.name, SUM(t.amount) AS total FROM employees e JOIN transactions t ON e.id = t.employee_id
        
        Question: Show all employees in Engineering
        CORRECT SQL: SELECT * FROM employees WHERE department ILIKE 'Engineering'
        WRONG SQL: SELECT * FROM employees WHERE department ILIKE 'Engineering' GROUP BY id, name
        
        Question: Show average salary for each department with total headcount
        CORRECT SQL: SELECT department, AVG(salary) AS avg_salary, COUNT(*) AS headcount FROM employees GROUP BY department ORDER BY avg_salary DESC
        WRONG SQL: SELECT department, AVG(salary), COUNT(*) FROM employees
        
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

    private String fixGroupBy(String sql) {
        String upperSql = sql.toUpperCase();

        // Check aggregation on ORIGINAL sql, not stripped version
        boolean hasAggregation = upperSql.contains("SUM(")
                || upperSql.contains("COUNT(")
                || upperSql.contains("AVG(")
                || upperSql.contains("MAX(")
                || upperSql.contains("MIN(");
        boolean hasGroupBy = upperSql.contains("GROUP BY");

        log.info("fixGroupBy — hasAggregation: {}, hasGroupBy: {}", hasAggregation, hasGroupBy);

        if (!hasAggregation || hasGroupBy) return sql;

        // Strip only subqueries (SELECT inside parentheses) not all parentheses
        String sqlWithoutSubqueries = upperSql.replaceAll("\\(SELECT[^)]*\\)", "");

        boolean hasAggregationInMain = sqlWithoutSubqueries.contains("SUM(")
                || sqlWithoutSubqueries.contains("COUNT(")
                || sqlWithoutSubqueries.contains("AVG(")
                || sqlWithoutSubqueries.contains("MAX(")
                || sqlWithoutSubqueries.contains("MIN(");

        if (!hasAggregationInMain) return sql; // aggregation only in subqueries, safe

        // Extract SELECT clause
        String selectClause = sql.substring(
                upperSql.indexOf("SELECT") + 6,
                upperSql.indexOf("FROM")
        ).trim();

        String[] columns = selectClause.split(",");
        String groupByCols = Arrays.stream(columns)
                .filter(col -> !col.toUpperCase().matches(".*?(SUM|COUNT|AVG|MAX|MIN)\\s*\\(.*"))
                .map(col -> {
                    String trimmed = col.trim();
                    int asIndex = trimmed.toUpperCase().indexOf(" AS ");
                    return asIndex > 0 ? trimmed.substring(0, asIndex).trim() : trimmed;
                })
                .filter(col -> !col.isEmpty()) // ← add this
                .collect(Collectors.joining(", "));

        // Only inject GROUP BY if there are non-aggregated columns
        if (groupByCols.isEmpty()) return sql;

        log.info("Auto-injecting GROUP BY: {}", groupByCols);

        if (upperSql.contains("ORDER BY")) {
            int orderByIndex = sql.toUpperCase().indexOf("ORDER BY");
            return sql.substring(0, orderByIndex).trim()
                    + " GROUP BY " + groupByCols + " "
                    + sql.substring(orderByIndex);
        }

        return sql + " GROUP BY " + groupByCols;
    }
}