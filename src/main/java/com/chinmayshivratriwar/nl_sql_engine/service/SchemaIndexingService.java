package com.chinmayshivratriwar.nl_sql_engine.service;

import com.chinmayshivratriwar.nl_sql_engine.exception.SchemaGraphException;
import com.chinmayshivratriwar.nl_sql_engine.model.SchemaEdge;
import com.chinmayshivratriwar.nl_sql_engine.model.SchemaGraph;
import com.chinmayshivratriwar.nl_sql_engine.model.TableNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

@Slf4j
@Service
public class SchemaIndexingService {

    private final EmbeddingModel embeddingModel;

    public SchemaIndexingService(@Qualifier("schemaEmbeddingModel") EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public SchemaGraph buildGraph(DataSource dataSource) {
        SchemaGraph graph = new SchemaGraph();

        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            String catalog = conn.getCatalog();
            String schema  = conn.getSchema();

            List<String> tables = extractTableNames(meta, catalog, schema);
            log.info("Schema indexing started. Tables found: {}", tables.size());

            for (String tableName : tables) {
                // 1. Extract columns
                List<String> columns = extractColumns(meta, catalog, schema, tableName);

                // 2. Extract FK relationships
                List<SchemaEdge> fkEdges = extractForeignKeys(meta, catalog, schema, tableName);

                // 3. Build a rich text chunk for this table
                String chunkText = buildChunkText(tableName, columns, fkEdges);

                // 4. Embed the chunk
                float[] embedding = embed(chunkText);

                // 5. Add node
                TableNode node = new TableNode(tableName, chunkText, embedding, columns);
                graph.addNode(node);

                // 6. Add edges
                fkEdges.forEach(graph::addEdge);

                log.debug("Indexed table: {} | columns: {} | fks: {}",
                        tableName, columns.size(), fkEdges.size());
            }

        } catch (SQLException e) {
            log.error("Schema indexing failed: {}", e.getMessage());
            throw new SchemaGraphException("Failed to build schema graph", e);
        }

        log.info("Schema graph built. Nodes: {} | Edges: {}", graph.nodeCount(), graph.edgeCount());
        return graph;
    }

    private List<String> extractTableNames(DatabaseMetaData meta,
                                           String catalog,
                                           String schema) throws SQLException {
        List<String> tables = new ArrayList<>();
        try (ResultSet rs = meta.getTables(catalog, schema, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                tables.add(rs.getString("TABLE_NAME"));
            }
        }
        return tables;
    }

    private List<String> extractColumns(DatabaseMetaData meta,
                                        String catalog,
                                        String schema,
                                        String tableName) throws SQLException {
        List<String> columns = new ArrayList<>();
        try (ResultSet rs = meta.getColumns(catalog, schema, tableName, "%")) {
            while (rs.next()) {
                String colName = rs.getString("COLUMN_NAME");
                String colType = rs.getString("TYPE_NAME");
                columns.add(colName + " (" + colType + ")");
            }
        }
        return columns;
    }

    private List<SchemaEdge> extractForeignKeys(DatabaseMetaData meta,
                                                String catalog,
                                                String schema,
                                                String tableName) throws SQLException {
        List<SchemaEdge> edges = new ArrayList<>();
        try (ResultSet rs = meta.getImportedKeys(catalog, schema, tableName)) {
            while (rs.next()) {
                String fkColumn      = rs.getString("FKCOLUMN_NAME");
                String referencedTable  = rs.getString("PKTABLE_NAME");
                String referencedColumn = rs.getString("PKCOLUMN_NAME");

                String relation = tableName + "." + fkColumn
                        + " → " + referencedTable + "." + referencedColumn;

                edges.add(new SchemaEdge(tableName, referencedTable, fkColumn, relation));
            }
        }
        return edges;
    }

    private String buildChunkText(String tableName,
                                  List<String> columns,
                                  List<SchemaEdge> edges) {
        StringBuilder sb = new StringBuilder();
        sb.append("Table: ").append(tableName).append("\n");
        sb.append("Columns: ").append(String.join(", ", columns)).append("\n");

        if (!edges.isEmpty()) {
            sb.append("Foreign Keys: ");
            edges.forEach(e -> sb.append(e.getRelation()).append(" | "));
            sb.append("\n");
        }

        return sb.toString().trim();
    }

    private float[] embed(String text) {
        return embeddingModel.embed(text);
    }
}
