package com.chinmayshivratriwar.nl_sql_engine.model;

import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class QueryResponse {
    private String question;
    private String generatedSql;
    private List<Map<String, Object>> results;
    private long executionTimeMs;
    private boolean fromCache;
    private String error;
}
