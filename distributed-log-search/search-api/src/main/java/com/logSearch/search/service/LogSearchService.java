package com.logSearch.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch._types.query_dsl.*;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import com.logSearch.common.model.LogEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LogSearchService {

    private final ElasticsearchClient esClient;

    @Value("${elasticsearch.index.prefix:applogs}")
    private String indexPrefix;

    public com.logSearch.common.dto.SearchResponse search(com.logSearch.common.dto.SearchRequest request)
            throws IOException {

        long startTime = System.currentTimeMillis();

        BoolQuery.Builder boolQuery = new BoolQuery.Builder();

        // Full-text search
        if (request.getQuery() != null && !request.getQuery().isBlank()) {
            String q = request.getQuery();
            boolQuery.must(m -> m.match(mm -> mm
                    .field("message")
                    .query(q)
            ));
        }

        // Service name filter
        if (request.getServiceName() != null && !request.getServiceName().isBlank()) {
            String svc = request.getServiceName();
            boolQuery.filter(f -> f.term(t -> t
                    .field("serviceName")
                    .value(v -> v.stringValue(svc))
            ));
        }

        // Host filter
        if (request.getHost() != null && !request.getHost().isBlank()) {
            String host = request.getHost();
            boolQuery.filter(f -> f.term(t -> t
                    .field("host")
                    .value(v -> v.stringValue(host))
            ));
        }

        // Trace ID filter
        if (request.getTraceId() != null && !request.getTraceId().isBlank()) {
            String traceId = request.getTraceId();
            boolQuery.filter(f -> f.term(t -> t
                    .field("traceId")
                    .value(v -> v.stringValue(traceId))
            ));
        }

        // Level filter
        if (request.getLevels() != null && !request.getLevels().isEmpty()) {
            List<FieldValue> fieldValues = new ArrayList<>();
            for (LogEvent.LogLevel level : request.getLevels()) {
                fieldValues.add(FieldValue.of(level.name()));
            }
            boolQuery.filter(f -> f.terms(t -> t
                    .field("level")
                    .terms(tv -> tv.value(fieldValues))
            ));
        }

        // Time range filter
        if (request.getFrom() != null || request.getTo() != null) {
            Instant from = request.getFrom();
            Instant to = request.getTo();
            boolQuery.filter(f -> f.range(r -> {
                r.field("timestamp");
                if (from != null) r.gte(JsonData.of(from.toString()));
                if (to != null)   r.lte(JsonData.of(to.toString()));
                return r;
            }));
        }

        int fromOffset = request.getPage() * request.getSize();
        SortOrder sortOrder = "asc".equalsIgnoreCase(request.getSortOrder())
                ? SortOrder.Asc : SortOrder.Desc;

        SearchRequest esRequest = SearchRequest.of(s -> s
                .index(indexPrefix + "-*")
                .query(q -> q.bool(boolQuery.build()))
                .from(fromOffset)
                .size(request.getSize())
                .sort(so -> so.field(f -> f.field("timestamp").order(sortOrder)))
                .aggregations("levelCounts", a -> a.terms(t -> t.field("level").size(10)))
                .aggregations("serviceCounts", a -> a.terms(t -> t.field("serviceName").size(20)))
                .trackTotalHits(th -> th.enabled(true))
        );

        SearchResponse<LogEvent> esResponse;
        try {
            esResponse = esClient.search(esRequest, LogEvent.class);
        } catch (Exception e) {
            log.error("Elasticsearch search failed: {}", e.getMessage(), e);
            throw new IOException("Search failed: " + e.getMessage(), e);
        }

        long tookMs = System.currentTimeMillis() - startTime;
        log.debug("Search completed in {}ms, found {} hits", tookMs,
                esResponse.hits().total().value());

        List<LogEvent> hits = esResponse.hits().hits().stream()
                .map(Hit::source)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        Map<String, Long> levelCounts   = extractTermAggregation(esResponse, "levelCounts");
        Map<String, Long> serviceCounts = extractTermAggregation(esResponse, "serviceCounts");

        long totalHits = esResponse.hits().total().value();
        int totalPages = (int) Math.ceil((double) totalHits / request.getSize());

        return com.logSearch.common.dto.SearchResponse.builder()
                .hits(hits)
                .totalHits(totalHits)
                .tookMs(tookMs)
                .page(request.getPage())
                .size(request.getSize())
                .totalPages(totalPages)
                .levelCounts(levelCounts)
                .serviceCounts(serviceCounts)
                .build();
    }

    public Optional<LogEvent> getById(String id) throws IOException {
        var response = esClient.search(s -> s
                        .index(indexPrefix + "-*")
                        .query(q -> q.ids(i -> i.values(id)))
                        .size(1),
                LogEvent.class
        );
        return response.hits().hits().stream()
                .map(Hit::source)
                .filter(Objects::nonNull)
                .findFirst();
    }

    public List<LogEvent> getByTraceId(String traceId) throws IOException {
        var response = esClient.search(s -> s
                        .index(indexPrefix + "-*")
                        .query(q -> q.term(t -> t
                                .field("traceId")
                                .value(v -> v.stringValue(traceId))
                        ))
                        .sort(so -> so.field(f -> f.field("timestamp").order(SortOrder.Asc)))
                        .size(1000),
                LogEvent.class
        );
        return response.hits().hits().stream()
                .map(Hit::source)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private Map<String, Long> extractTermAggregation(SearchResponse<LogEvent> response, String aggName) {
        try {
            var agg = response.aggregations().get(aggName);
            if (agg == null) return Map.of();
            var sterms = agg.sterms();
            if (sterms == null) return Map.of();
            return sterms.buckets().array().stream()
                    .collect(Collectors.toMap(
                            bucket -> bucket.key().stringValue(),
                            StringTermsBucket::docCount,
                            (a, b) -> a,
                            LinkedHashMap::new
                    ));
        } catch (Exception e) {
            log.debug("Could not extract aggregation '{}': {}", aggName, e.getMessage());
            return Map.of();
        }
    }
}