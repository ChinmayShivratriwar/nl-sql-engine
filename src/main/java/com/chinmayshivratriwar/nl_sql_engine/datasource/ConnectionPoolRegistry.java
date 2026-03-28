package com.chinmayshivratriwar.nl_sql_engine.datasource;

import com.chinmayshivratriwar.nl_sql_engine.model.DatabaseCredentials;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class ConnectionPoolRegistry {

    // TODO: MULTI-INSTANCE PROBLEM
    // This registry lives in JVM memory. In a multi-instance deployment,
    // each instance maintains its own registry — meaning the same user
    // can have duplicate pools across instances, wasting DB connections
    // and memory. When scaling to multiple instances, this must be
    // replaced with a Redis-backed coordination layer where connection
    // metadata is shared, but HikariCP pools remain local per instance.
    // Review this before any horizontal scaling decision.
    private final ConcurrentHashMap<String, PoolEntry> registry = new ConcurrentHashMap<>();

    private static final int MAX_POOL_SIZE = 3;
    private static final int CONNECTION_TIMEOUT_MS = 5000;
    private static final int IDLE_TIMEOUT_MINUTES = 30;

    public DataSource getOrCreatePool(DatabaseCredentials credentials) {
        String key = buildKey(credentials);

        PoolEntry entry = registry.compute(key, (k, existing) -> {
            if (existing != null && !existing.isExpired()) {
                existing.updateLastAccessed();
                log.info("Reusing existing pool for key: {}", maskKey(key));
                return existing;
            }
            if (existing != null) {
                log.info("Pool expired, creating new one for key: {}", maskKey(key));
                existing.close();
            }
            log.info("Creating new connection pool for key: {}", maskKey(key));
            return new PoolEntry(createPool(credentials));
        });

        return entry.dataSource;
    }

    private HikariDataSource createPool(DatabaseCredentials credentials) {
        // Use SSL for non-local connections only
        boolean isLocal = credentials.getHost().equals("localhost")
                || credentials.getHost().equals("127.0.0.1");

        String url = String.format(
                "jdbc:postgresql://%s:%s/%s%s",
                credentials.getHost(),
                credentials.getPort(),
                credentials.getDatabaseName(),
                isLocal ? "" : "?sslmode=require"
        );

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(credentials.getUsername());
        config.setPassword(credentials.getPassword());
        config.setDriverClassName("org.postgresql.Driver");
        config.setMaximumPoolSize(MAX_POOL_SIZE);
        config.setConnectionTimeout(CONNECTION_TIMEOUT_MS);
        config.setMaxLifetime(IDLE_TIMEOUT_MINUTES * 2 * 60 * 1000L);

        log.info("Creating pool for host: {} db: {} ssl: {}",
                credentials.getHost(), credentials.getDatabaseName(), !isLocal);

        return new HikariDataSource(config);
    }

    public boolean validateConnection(DatabaseCredentials credentials) {
        try {
            DataSource ds = getOrCreatePool(credentials);
            ds.getConnection().close();
            log.info("Connection validated for host: {}", credentials.getHost());
            return true;
        } catch (Exception e) {
            log.error("Connection validation failed for host: {} error: {}",
                    credentials.getHost(), e.getMessage());
            return false;
        }
    }

    // Scheduler 1 — evicts pools idle for more than 14 minutes
    // Runs every 5 minutes
    @Scheduled(fixedDelay = 300000)
    public void evictExpiredPools() {
        log.info("Idle eviction running. Pool count: {}", registry.size());
        registry.entrySet().removeIf(entry -> {
            if (entry.getValue().isExpired()) {
                entry.getValue().close();
                log.info("Evicted idle pool: {}", maskKey(entry.getKey()));
                return true;
            }
            return false;
        });
        log.info("Idle eviction complete. Remaining pools: {}", registry.size());
    }

    // Scheduler 2 — hard reset every 14 minutes
    // Evicts ALL pools — active or not
    // Reason: Render free tier spins down after 15 minutes of inactivity
    // Before spin down, all pools must be invalidated and closed cleanly
    // so no stale HikariCP connections exist on restart

    @Scheduled(fixedDelay = 840000)
    public void hardResetAllPools() {
        log.info("Hard reset running. Closing all {} pools.", registry.size());
        registry.forEach((key, entry) -> {
            entry.close();
            log.info("Force closed pool: {}", maskKey(key));
        });
        registry.clear();
        log.info("Hard reset complete. All pools evicted.");
    }

    // Key is host + port + db + username — password never stored
    private String buildKey(DatabaseCredentials credentials) {
        return credentials.getHost() + ":" +
                credentials.getPort() + "/" +
                credentials.getDatabaseName() + "@" +
                credentials.getUsername();
    }

    // Mask key in logs for security
    private String maskKey(String key) {
        return key.substring(0, Math.min(10, key.length())) + "***";
    }

    // ── Inner class ──
    private static class PoolEntry {
        final HikariDataSource dataSource;
        volatile LocalDateTime lastAccessedAt;

        PoolEntry(HikariDataSource dataSource) {
            this.dataSource = dataSource;
            this.lastAccessedAt = LocalDateTime.now();
        }

        void updateLastAccessed() {
            this.lastAccessedAt = LocalDateTime.now();
        }

        boolean isExpired() {
            return lastAccessedAt.isBefore(
                    LocalDateTime.now().minusMinutes(30)
            );
        }

        void close() {
            if (dataSource != null && !dataSource.isClosed()) {
                dataSource.close();
            }
        }
    }
}