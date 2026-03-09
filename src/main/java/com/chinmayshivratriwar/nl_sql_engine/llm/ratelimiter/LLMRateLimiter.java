package com.chinmayshivratriwar.nl_sql_engine.llm.ratelimiter;

public interface LLMRateLimiter {
    void validateAndConsume();
}