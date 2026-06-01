package com.chinmayshivratriwar.nl_sql_engine.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
public class TableNode {
    private final String tableName;
    private final String chunkText;      // what gets injected into prompt
    private final float[] embedding;     // vector representation of chunkText
    private final List<String> columns;  // for quick reference
}
