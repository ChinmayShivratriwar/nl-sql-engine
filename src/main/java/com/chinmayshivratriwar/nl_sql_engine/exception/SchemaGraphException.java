package com.chinmayshivratriwar.nl_sql_engine.exception;

public class SchemaGraphException extends RuntimeException {
    public SchemaGraphException(String message) {
        super(message);
    }
    public SchemaGraphException(String message, Exception cause) {
        super(message, cause);
    }
}
