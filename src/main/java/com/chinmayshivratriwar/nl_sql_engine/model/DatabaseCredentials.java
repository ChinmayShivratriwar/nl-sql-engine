
package com.chinmayshivratriwar.nl_sql_engine.model;

import lombok.Data;

@Data
public class DatabaseCredentials {
    private String host;
    private String port;
    private String databaseName;
    private String username;
    private String password;
}
