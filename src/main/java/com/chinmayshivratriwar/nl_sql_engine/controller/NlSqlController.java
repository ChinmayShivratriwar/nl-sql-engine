package com.chinmayshivratriwar.nl_sql_engine.controller;
import com.chinmayshivratriwar.nl_sql_engine.model.QueryRequest;
import com.chinmayshivratriwar.nl_sql_engine.model.QueryResponse;
import com.chinmayshivratriwar.nl_sql_engine.service.NlSqlService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/query")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class NlSqlController {

    private final NlSqlService nlSqlService;

    @PostMapping
    public ResponseEntity<QueryResponse> query(@RequestBody QueryRequest request) {
        QueryResponse response = nlSqlService.processQuery(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("nl-sql-engine is running");
    }
}
