package com.chinmayshivratriwar.nl_sql_engine.service;

import com.chinmayshivratriwar.nl_sql_engine.datasource.ConnectionPoolRegistry;
import com.chinmayshivratriwar.nl_sql_engine.model.DatabaseCredentials;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DynamicDataSourceService {

    private final ConnectionPoolRegistry connectionPoolRegistry;

    public boolean validateConnection(DatabaseCredentials credentials) {
        return connectionPoolRegistry.validateConnection(credentials);
    }

    public String extractSchema(DatabaseCredentials credentials) {
        DataSource dataSource = connectionPoolRegistry.getOrCreatePool(credentials);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        List<String> tables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables " +
                        "WHERE table_schema = 'public' AND table_type = 'BASE TABLE'",
                String.class
        );

        StringBuilder schema = new StringBuilder();
        for (String table : tables) {
            schema.append("Table: ").append(table).append("\n");
            schema.append("Columns:\n");

            List<Map<String, Object>> columns = jdbcTemplate.queryForList(
                    "SELECT column_name, data_type, is_nullable " +
                            "FROM information_schema.columns " +
                            "WHERE table_schema = 'public' AND table_name = ? " +
                            "ORDER BY ordinal_position",
                    table
            );

            for (Map<String, Object> column : columns) {
                schema.append("  - ")
                        .append(column.get("column_name"))
                        .append(" (")
                        .append(column.get("data_type"))
                        .append(")\n");
            }
            schema.append("\n");
        }

        log.info("Extracted schema for {} tables from user DB: {}",
                tables.size(), credentials.getHost());
        return schema.toString();
    }

    public List<Map<String, Object>> executeQuery(
            DatabaseCredentials credentials, String sql) {
        DataSource dataSource = connectionPoolRegistry.getOrCreatePool(credentials);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        log.info("Executing query on user DB: {}", credentials.getHost());
        return jdbcTemplate.queryForList(sql);
    }
}
