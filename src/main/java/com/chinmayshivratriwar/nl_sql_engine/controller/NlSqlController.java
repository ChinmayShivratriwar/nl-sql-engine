package com.chinmayshivratriwar.nl_sql_engine.controller;

import com.chinmayshivratriwar.nl_sql_engine.model.QueryRequest;
import com.chinmayshivratriwar.nl_sql_engine.model.QueryResponse;
import com.chinmayshivratriwar.nl_sql_engine.service.NlSqlService;
import com.chinmayshivratriwar.nl_sql_engine.service.RateLimiterService;
import com.chinmayshivratriwar.nl_sql_engine.service.SchemaService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/query")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class NlSqlController {

    @Value("${app.schema.access.key}")
    private String schemaAccessKey;

    private final NlSqlService nlSqlService;
    private final RateLimiterService rateLimiterService;
    private final SchemaService schemaService;

    @PostMapping
    public ResponseEntity<?> query(
            @RequestBody QueryRequest request,
            HttpServletRequest httpRequest) {

        String ipAddress = getClientIP(httpRequest);

        if (!rateLimiterService.tryConsume(ipAddress)) {
            log.info("Rate limit exceeded for IP: {}", ipAddress);
            return ResponseEntity
                    .status(HttpStatus.TOO_MANY_REQUESTS)
                    .body("Rate limit exceeded. I know i am interesting but stop spamming.");
        }

        QueryResponse response = nlSqlService.processQuery(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("nl-sql-engine is running");
    }

    @GetMapping("/schema")
    public ResponseEntity<?> getSchema(
            @RequestHeader(value = "X-Internal-Key", required = true) String internalKey) {

        if (internalKey == null || !internalKey.equals(schemaAccessKey)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Access denied.");
        }

        String schema = schemaService.extractSchema();
        return ResponseEntity.ok(schema);
    }

    private String getClientIP(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isEmpty()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}