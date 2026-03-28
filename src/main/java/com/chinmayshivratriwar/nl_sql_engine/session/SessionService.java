package com.chinmayshivratriwar.nl_sql_engine.session;


import com.chinmayshivratriwar.nl_sql_engine.model.DatabaseCredentials;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class SessionService {

    // TODO: MULTI-INSTANCE PROBLEM
    // In-memory session store does not share across instances.
    // In a multi-instance deployment, a token created on Instance 1
    // will not be resolvable on Instance 2.
    // Replace with Redis-backed session store when scaling horizontally.
    // Review before any horizontal scaling decision.
    private final ConcurrentHashMap<String, SessionEntry> sessions
            = new ConcurrentHashMap<>();

    private static final int SESSION_TIMEOUT_MINUTES = 30;

    public String createSession(DatabaseCredentials credentials) {
        String token = UUID.randomUUID().toString();
        sessions.put(token, new SessionEntry(credentials));
        log.info("Session created for host: {} db: {}",
                credentials.getHost(), credentials.getDatabaseName());
        return token;
    }

    public DatabaseCredentials resolve(String token) {
        SessionEntry entry = sessions.get(token);

        if (entry == null) {
            throw new RuntimeException(
                    "Session not found. Please reconnect your database.");
        }

        if (entry.isExpired()) {
            sessions.remove(token);
            log.info("Session expired and removed.");
            throw new RuntimeException(
                    "Session expired after 30 minutes of inactivity. Please reconnect.");
        }

        entry.updateLastAccessed();
        return entry.credentials;
    }

    public void invalidateSession(String token) {
        sessions.remove(token);
        log.info("Session invalidated.");
    }

    // Runs every 5 minutes — evicts sessions idle for more than 30 minutes
    @Scheduled(fixedDelay = 300000)
    public void evictExpiredSessions() {
        log.info("Running session eviction. Current count: {}", sessions.size());
        sessions.entrySet().removeIf(entry -> {
            if (entry.getValue().isExpired()) {
                log.info("Evicting expired session.");
                return true;
            }
            return false;
        });
        log.info("Session eviction complete. Remaining: {}", sessions.size());
    }

    private static class SessionEntry {
        final DatabaseCredentials credentials;
        volatile LocalDateTime lastAccessedAt;

        SessionEntry(DatabaseCredentials credentials) {
            this.credentials = credentials;
            this.lastAccessedAt = LocalDateTime.now();
        }

        void updateLastAccessed() {
            this.lastAccessedAt = LocalDateTime.now();
        }

        boolean isExpired() {
            return lastAccessedAt.isBefore(
                    LocalDateTime.now().minusMinutes(SESSION_TIMEOUT_MINUTES)
            );
        }
    }
}
