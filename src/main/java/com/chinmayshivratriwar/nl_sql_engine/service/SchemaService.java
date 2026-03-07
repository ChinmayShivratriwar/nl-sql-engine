package com.chinmayshivratriwar.nl_sql_engine.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SchemaService {

    private final JdbcTemplate jdbcTemplate;

    public String extractSchema() {
        // Get all tables in public schema
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
                        .append(", nullable: ")
                        .append(column.get("is_nullable"))
                        .append(")\n");
            }
            schema.append("\n");
        }

        log.info("Extracted schema for {} tables", tables.size());
        return schema.toString();
    }
}