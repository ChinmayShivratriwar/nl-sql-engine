package com.chinmayshivratriwar.nl_sql_engine.model;

import java.util.List;

public record RetrievalResult(String schemaText, List<String> retrievedTables) {}
