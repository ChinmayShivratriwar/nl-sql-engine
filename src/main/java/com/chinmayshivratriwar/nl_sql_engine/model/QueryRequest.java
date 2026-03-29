package com.chinmayshivratriwar.nl_sql_engine.model;

import lombok.Data;

@Data
public class QueryRequest {
    private String question;
    private DatabaseCredentials credentials; // only used for /connect
    private String sessionToken;             // used for all subsequent queries
}