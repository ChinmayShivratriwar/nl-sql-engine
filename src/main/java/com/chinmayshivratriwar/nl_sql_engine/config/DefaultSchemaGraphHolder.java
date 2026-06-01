package com.chinmayshivratriwar.nl_sql_engine.config;

import com.chinmayshivratriwar.nl_sql_engine.model.SchemaGraph;
import com.chinmayshivratriwar.nl_sql_engine.service.SchemaIndexingService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

@Slf4j
@Component
public class DefaultSchemaGraphHolder {

    private volatile SchemaGraph schemaGraph;
    private final SchemaIndexingService schemaIndexingService;
    private final DataSource defaultDataSource;  // Spring injects this

    public DefaultSchemaGraphHolder(SchemaIndexingService schemaIndexingService,
                                    DataSource defaultDataSource) {
        this.schemaIndexingService = schemaIndexingService;
        this.defaultDataSource = defaultDataSource;
    }

    @PostConstruct
    public void init() {
        log.info("Building schema graph for default DB...");
        this.schemaGraph = schemaIndexingService.buildGraph(defaultDataSource);
        log.info("Default DB schema graph built. Nodes: {}", schemaGraph.nodeCount());
    }

    public SchemaGraph getGraph() {
        return schemaGraph;
    }

    // Call this if schema changes at runtime
    public void reindex() {
        this.schemaGraph = schemaIndexingService.buildGraph(defaultDataSource);
    }
}
