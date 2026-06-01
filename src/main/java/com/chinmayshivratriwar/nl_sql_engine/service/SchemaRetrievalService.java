package com.chinmayshivratriwar.nl_sql_engine.service;

import com.chinmayshivratriwar.nl_sql_engine.model.RetrievalResult;
import com.chinmayshivratriwar.nl_sql_engine.model.SchemaEdge;
import com.chinmayshivratriwar.nl_sql_engine.model.SchemaGraph;
import com.chinmayshivratriwar.nl_sql_engine.model.TableNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SchemaRetrievalService {

    private int topKTables = 2;    // seed tables from vector search
    private int schemaRelationDepth = 2; // FK hops to expand range of tables

    private final EmbeddingModel embeddingModel;

    public SchemaRetrievalService(@Qualifier("schemaEmbeddingModel") EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public RetrievalResult retrieve(SchemaGraph graph, String question) {
        // 1. Embed the question
        float[] questionEmbedding = embed(question);

        // 2. Cosine similarity against all nodes → top K seed tables
        topKTables = resolveTopK(graph, question);
        List<String> seedTables = similaritySearch(graph, questionEmbedding, topKTables);
        log.debug("Seed tables for question '{}': {}", question, seedTables);

        // 3. BFS expansion on FK edges
        schemaRelationDepth = resolveBfsDepth(graph);
        Set<String> allRelevantTables = bfsExpand(graph, seedTables, schemaRelationDepth);
        log.debug("Tables after BFS expansion: {}", allRelevantTables);

        // 4. Collect chunk texts for all relevant tables
        String schemaText = allRelevantTables.stream()
                .map(graph::getNode)
                .filter(Objects::nonNull)
                .map(TableNode::getChunkText)
                .collect(Collectors.joining("\n\n"));

        return new RetrievalResult(schemaText, new ArrayList<>(allRelevantTables));
    }


    private List<String> generousSimilaritySearch(SchemaGraph graph,
                                          float[] questionEmbedding,
                                          int topK) {
        return graph.getAllNodes().stream()
                .map(node -> Map.entry(node.getTableName(),
                        cosineSimilarity(questionEmbedding, node.getEmbedding())))
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    private List<String> similaritySearch(SchemaGraph graph,
                                          float[] questionEmbedding,
                                          int topK) {
        double THRESHOLD = 0.35; //Hugging Face Standard, Needs to be configurable via feature flags (TBD)

        return graph.getAllNodes().stream()
                .map(node -> {
                    double score = cosineSimilarity(questionEmbedding, node.getEmbedding());
                    log.debug("Table: {} | Score: {}", node.getTableName(), score);  // ← here
                    return Map.entry(node.getTableName(), score);
                })
                .filter(entry -> {
                    boolean passes = entry.getValue() >= THRESHOLD;
                    if (!passes) log.debug("Table: {} filtered out by threshold", entry.getKey()); // ← and here
                    return passes;
                })
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    private Set<String> bfsExpand(SchemaGraph graph,
                                  List<String> seeds,
                                  int maxDepth) {
        Set<String> visited = new LinkedHashSet<>(seeds);  // LinkedHashSet preserves insertion order
        Queue<String> queue = new LinkedList<>(seeds);
        int depth = 0;

        while (!queue.isEmpty() && depth < maxDepth) {
            int levelSize = queue.size();

            for (int i = 0; i < levelSize; i++) {
                String current = queue.poll();
                for (SchemaEdge edge : graph.getEdges(current)) {
                    if (!visited.contains(edge.getToTable())) {
                        visited.add(edge.getToTable());
                        queue.add(edge.getToTable());
                    }
                }
            }
            depth++;
        }
        return visited;
    }

    private double cosineSimilarity(float[] a, float[] b) {
        double dot = 0.0, normA = 0.0, normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot   += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0.0 || normB == 0.0) return 0.0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private float[] embed(String text) {
        return embeddingModel.embed(text);
    }

    private int resolveTopK(SchemaGraph graph, String question) {
        int totalTables = graph.nodeCount();
        int ceiling = totalTables / 2;

        boolean isComplex = question.toLowerCase().contains(" and ")
                || question.toLowerCase().contains(" with ")
                || question.toLowerCase().contains("across");

        int base = Math.max(1, totalTables / 3);
        int resolved = isComplex ? base + 1 : base;
        // return Math.min(resolved, ceiling); to be used when running extremely low on resources or dealing with very large schemas, otherwise return resolved for better performance
        return resolved;
    }

    private int resolveBfsDepth(SchemaGraph graph) {
        int diameter = graph.computeDiameter();
        return Math.min(4, Math.max(2, diameter / 2));
    }
}
