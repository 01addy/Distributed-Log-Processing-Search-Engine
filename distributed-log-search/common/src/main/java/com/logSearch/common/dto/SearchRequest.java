package com.logSearch.common.dto;

import com.logSearch.common.model.LogEvent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchRequest {

    // Full-text search query string 
    private String query;

    // Filter by specific service name
    private String serviceName;

    // Filter by host
    private String host;

    // Filter by log level(s)
    private List<LogEvent.LogLevel> levels;

    // Start of time range (inclusive)
    private Instant from;

    // End of time range
    private Instant to;

    // Trace ID for correlated log search
    private String traceId;

    // Pagination: page number
    @Builder.Default
    private int page = 0;

    // Pagination: results per page
    @Builder.Default
    private int size = 50;

    // Sort order: asc or desc by timestamp
    @Builder.Default
    private String sortOrder = "desc";
}
