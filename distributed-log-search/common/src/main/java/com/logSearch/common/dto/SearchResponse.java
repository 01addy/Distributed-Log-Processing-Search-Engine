package com.logSearch.common.dto;

import com.logSearch.common.model.LogEvent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * DTO for search API responses.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResponse {

    /** Matching log events */
    private List<LogEvent> hits;

    /** Total number of matching documents */
    private long totalHits;

    /** Time taken for the search in milliseconds */
    private long tookMs;

    /** Current page number */
    private int page;

    /** Page size used */
    private int size;

    /** Total number of pages */
    private int totalPages;

    /** Aggregations: log level counts */
    private Map<String, Long> levelCounts;

    /** Aggregations: top services */
    private Map<String, Long> serviceCounts;
}
