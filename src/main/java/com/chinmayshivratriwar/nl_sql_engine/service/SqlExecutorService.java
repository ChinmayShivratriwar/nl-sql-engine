package com.chinmayshivratriwar.nl_sql_engine.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SqlExecutorService {

    private final JdbcTemplate jdbcTemplate;

    // Safety — block destructive queries
    private static final List<String> BLOCKED_KEYWORDS = List.of(
            "DROP", "DELETE", "TRUNCATE", "ALTER", "INSERT", "UPDATE"
    );

    public List<Map<String, Object>> execute(String sql) {
        String upperSql = sql.toUpperCase();

        for (String keyword : BLOCKED_KEYWORDS) {
            if (upperSql.contains(keyword)) {
                throw new RuntimeException(
                        "Destructive SQL operation not allowed: " + keyword
                );
            }
        }
        // Validate GROUP BY usage
        if (upperSql.contains("GROUP BY") && !upperSql.contains("COUNT(")
                && !upperSql.contains("SUM(") && !upperSql.contains("AVG(")
                && !upperSql.contains("MAX(") && !upperSql.contains("MIN(")) {
            throw new RuntimeException(
                    "Invalid SQL: GROUP BY used without aggregation function"
            );
        }

        // Block multiple statements
        String[] statements = sql.split(";");
        long nonEmptyStatements = Arrays.stream(statements)
                .map(String::trim)
                .filter(statement -> !statement.isEmpty())
                .count();

        if (nonEmptyStatements > 1) {
            throw new RuntimeException(
                    "Multiple SQL statements not allowed. Ask one question at a time."
            );
        }

        // Remove everything between parentheses before checking
        String sqlWithoutSubqueries = upperSql.replaceAll("\\(.*?\\)", "");

        boolean hasAggregation = sqlWithoutSubqueries.contains("SUM(")
                || sqlWithoutSubqueries.contains("COUNT(")
                || sqlWithoutSubqueries.contains("AVG(")
                || sqlWithoutSubqueries.contains("MAX(")
                || sqlWithoutSubqueries.contains("MIN(");
        boolean hasGroupBy = upperSql.contains("GROUP BY");

        if (hasAggregation && !hasGroupBy) {
            throw new RuntimeException(
                    "Invalid SQL: Aggregation function used without GROUP BY clause."
            );
        }
        log.info("Executing SQL: {}", sql);
        return jdbcTemplate.queryForList(sql);
    }
}
