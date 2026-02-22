package com.logSearch.search.controller;

import com.logSearch.common.dto.SearchRequest;
import com.logSearch.common.dto.SearchResponse;
import com.logSearch.common.model.LogEvent;
import com.logSearch.search.service.LogSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;


@Slf4j
@CrossOrigin(origins = "http://localhost:3000")
@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchController {

    private final LogSearchService searchService;


    @GetMapping
    public ResponseEntity<SearchResponse> search(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String serviceName,
            @RequestParam(required = false) String host,
            @RequestParam(required = false) List<LogEvent.LogLevel> levels,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String traceId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "desc") String sortOrder
    ) throws IOException {

        // Clamp page size to prevent runaway queries
        size = Math.min(size, 500);

        SearchRequest request = SearchRequest.builder()
                .query(query)
                .serviceName(serviceName)
                .host(host)
                .levels(levels)
                .from(from)
                .to(to)
                .traceId(traceId)
                .page(page)
                .size(size)
                .sortOrder(sortOrder)
                .build();

        log.debug("Search request: query={}, service={}, levels={}, from={}, to={}",
                query, serviceName, levels, from, to);

        SearchResponse response = searchService.search(request);
        return ResponseEntity.ok(response);
    }


    @PostMapping
    public ResponseEntity<SearchResponse> advancedSearch(
            @RequestBody SearchRequest request) throws IOException {
        request.setSize(Math.min(request.getSize(), 500));
        SearchResponse response = searchService.search(request);
        return ResponseEntity.ok(response);
    }


    @GetMapping("/{id}")
    public ResponseEntity<LogEvent> getById(@PathVariable String id) throws IOException {
        return searchService.getById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/trace/{traceId}")
    public ResponseEntity<Map<String, Object>> getByTraceId(@PathVariable String traceId) throws IOException {
        List<LogEvent> events = searchService.getByTraceId(traceId);
        return ResponseEntity.ok(Map.of(
                "traceId", traceId,
                "count", events.size(),
                "logs", events
        ));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "service", "search-api"));
    }
}
