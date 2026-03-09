package com.chinmayshivratriwar.nl_sql_engine.llm.ratelimiter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Component("groqRateLimiter")
public class GroqRateLimiter implements LLMRateLimiter {

    private final AtomicInteger dailyRequestCount = new AtomicInteger(0);
    private final AtomicReference<LocalDate> currentDay =
            new AtomicReference<>(LocalDate.now());

    private final AtomicInteger minuteRequestCount = new AtomicInteger(0);
    private final AtomicReference<LocalDateTime> minuteWindowStart =
            new AtomicReference<>(LocalDateTime.now());

    private static final int DAILY_LIMIT = 300;
    private static final int MINUTE_LIMIT = 25;

    @Override
    public void validateAndConsume() {
        resetDailyCountIfNewDay();
        resetMinuteCountIfWindowExpired();
        checkLimits();

        dailyRequestCount.incrementAndGet();
        minuteRequestCount.incrementAndGet();

        log.info("Groq usage — Daily: {}/{}, Minute: {}/{}",
                dailyRequestCount.get(), DAILY_LIMIT,
                minuteRequestCount.get(), MINUTE_LIMIT);
    }

    private void checkLimits() {
        if (dailyRequestCount.get() >= DAILY_LIMIT) {
            log.warn("Daily Groq limit reached: {}/{}",
                    dailyRequestCount.get(), DAILY_LIMIT);
            throw new RuntimeException(
                    "Daily query limit reached. Please try again tomorrow."
            );
        }
        if (minuteRequestCount.get() >= MINUTE_LIMIT) {
            log.warn("Per-minute Groq limit reached: {}/{}",
                    minuteRequestCount.get(), MINUTE_LIMIT);
            throw new RuntimeException(
                    "Too many requests. Please wait a minute and try again."
            );
        }
    }

    private void resetDailyCountIfNewDay() {
        LocalDate today = LocalDate.now();
        if (!currentDay.get().equals(today)) {
            currentDay.set(today);
            dailyRequestCount.set(0);
            log.info("Daily Groq count reset");
        }
    }

    private void resetMinuteCountIfWindowExpired() {
        LocalDateTime now = LocalDateTime.now();
        if (minuteWindowStart.get().plusMinutes(1).isBefore(now)) {
            minuteWindowStart.set(now);
            minuteRequestCount.set(0);
        }
    }
}
