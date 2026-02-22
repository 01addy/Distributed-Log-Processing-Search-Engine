package com.logSearch.ingestion.controller;

import com.logSearch.common.dto.LogIngestionRequest;
import com.logSearch.common.model.LogEvent;
import com.logSearch.ingestion.service.LogIngestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;


@Slf4j
@CrossOrigin(origins = "http://localhost:3000")
@RestController
@RequestMapping("/api/v1/logs")
@RequiredArgsConstructor

public class LogIngestionController {

    private final LogIngestionService ingestionService;


    @PostMapping
    public ResponseEntity<Map<String, Object>> ingestSingle(
            @Valid @RequestBody LogIngestionRequest request) {

        if (request.getLog() == null || request.getLog().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Field 'log' is required for single ingestion"));
        }

        log.debug("Received single log from service: {}", request.getServiceName());
        LogEvent event = ingestionService.ingestSingle(request);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "status", "accepted",
                "eventId", event.getId(),
                "ingestedAt", event.getIngestedAt().toString()
        ));
    }


    @PostMapping("/batch")
    public ResponseEntity<Map<String, Object>> ingestBatch(
            @Valid @RequestBody LogIngestionRequest request) {

        if (request.getLogs() == null || request.getLogs().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Field 'logs' is required for batch ingestion"));
        }

        int requestedCount = request.getLogs().size();
        log.debug("Received batch of {} logs from service: {}", requestedCount, request.getServiceName());

        List<LogEvent> events = ingestionService.ingestBatch(request);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "status", "accepted",
                "requestedCount", requestedCount,
                "acceptedCount", events.size(),
                "serviceName", request.getServiceName()
        ));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "log-ingestion"
        ));
    }
}
