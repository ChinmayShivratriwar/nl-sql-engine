package com.chinmayshivratriwar.nl_sql_engine.model;

import java.util.*;
import java.util.stream.Collectors;

public class SchemaGraph {

    // tableName → TableNode (holds embedding + chunkText)
    private final Map<String, TableNode> nodes = new HashMap<>();

    // tableName → list of foreign key edges going out from it
    private final Map<String, List<SchemaEdge>> adjacencyList = new HashMap<>();

    public void addNode(TableNode node) {
        nodes.put(node.getTableName(), node);
    }

    public void addEdge(SchemaEdge edge) {
        adjacencyList
                .computeIfAbsent(edge.getFromTable(), k -> new ArrayList<>())
                .add(edge);
    }

    public Collection<TableNode> getAllNodes() {
        return nodes.values();
    }

    public TableNode getNode(String tableName) {
        return nodes.get(tableName);
    }

    public List<SchemaEdge> getEdges(String tableName) {
        List<SchemaEdge> outgoing = adjacencyList.getOrDefault(tableName, Collections.emptyList());

        //Bidirectional GraPh: also include incoming edges
        List<SchemaEdge> incoming = adjacencyList.values().stream()
                .flatMap(List::stream)
                .filter(edge -> edge.getToTable().equals(tableName))
                .collect(Collectors.toList());

        List<SchemaEdge> all = new ArrayList<>(outgoing);
        all.addAll(incoming);
        return all;
    }

    public int nodeCount() { return nodes.size(); }
    public int edgeCount() { return adjacencyList.values().stream().mapToInt(List::size).sum(); }

    public int computeDiameter() {
        int diameter = 0;
        List<String> tableNames = new ArrayList<>(nodes.keySet());

        for (String source : tableNames) {
            Map<String, Integer> distances = bfsDistances(source);
            int maxFromSource = distances.values().stream()
                    .mapToInt(Integer::intValue)
                    .max()
                    .orElse(0);
            diameter = Math.max(diameter, maxFromSource);
        }
        return diameter;
    }

    private Map<String, Integer> bfsDistances(String source) {
        Map<String, Integer> distances = new HashMap<>();
        Queue<String> queue = new LinkedList<>();
        distances.put(source, 0);
        queue.add(source);

        while (!queue.isEmpty()) {
            String current = queue.poll();
            int currentDist = distances.get(current);

            for (SchemaEdge edge : getEdges(current)) {
                if (!distances.containsKey(edge.getToTable())) {
                    distances.put(edge.getToTable(), currentDist + 1);
                    queue.add(edge.getToTable());
                }
            }
        }
        return distances;
    }
}