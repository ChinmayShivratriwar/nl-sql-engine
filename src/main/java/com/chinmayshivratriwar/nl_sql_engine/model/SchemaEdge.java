package com.chinmayshivratriwar.nl_sql_engine.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class SchemaEdge {
    private final String fromTable;
    private final String toTable;
    private final String fkColumn;
    private final String relation;   // e.g. "orders.user_id → users.id"
}