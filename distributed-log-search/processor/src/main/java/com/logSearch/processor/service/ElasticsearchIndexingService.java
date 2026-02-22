package com.logSearch.processor.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import com.logSearch.common.model.LogEvent;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;


@Slf4j
@Service
@RequiredArgsConstructor
public class ElasticsearchIndexingService {

    private final ElasticsearchClient esClient;

    @Value("${elasticsearch.index.prefix:logs}")
    private String indexPrefix;

    @Value("${elasticsearch.index.shards:3}")
    private int numberOfShards;

    @Value("${elasticsearch.index.replicas:1}")
    private int numberOfReplicas;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");


    @PostConstruct
    public void initializeIndexTemplate() {
        try {
            // Delete old template
            try {
                esClient.indices().deleteIndexTemplate(d -> d.name("logs-template"));
            } catch (Exception ignored) {}

            // Create component-free regular index template (NO dataStream)
            esClient.indices().putIndexTemplate(t -> t
                    .name("logs-template")
                    .indexPatterns("logs-*")
                    .template(tmpl -> tmpl
                            .settings(s -> s
                                    .numberOfShards("3")
                                    .numberOfReplicas("1")
                                    .refreshInterval(ri -> ri.time("5s"))
                            )
                            .mappings(m -> m
                                    .properties("id", p -> p.keyword(k -> k))
                                    .properties("timestamp", p -> p.date(d -> d))
                                    .properties("ingestedAt", p -> p.date(d -> d))
                                    .properties("level", p -> p.keyword(k -> k))
                                    .properties("message", p -> p.text(tx -> tx.analyzer("standard")))
                                    .properties("serviceName", p -> p.keyword(k -> k))
                                    .properties("host", p -> p.keyword(k -> k))
                                    .properties("traceId", p -> p.keyword(k -> k))
                                    .properties("stackTrace", p -> p.text(tx -> tx))
                                    .properties("logger", p -> p.keyword(k -> k))
                            )
                    )
            );
            log.info("Index template created successfully");
        } catch (IOException e) {
            log.error("Failed to create index template: {}", e.getMessage(), e);
        }
    }


    public void index(LogEvent event) throws IOException {
        String indexName = getIndexName(event);
        esClient.index(i -> i
                .index(indexName)
                .id(event.getId())
                .document(event)
        );
    }

    /**
     * Bulk index a list of log events for high throughput.
     * This is the primary indexing path — processes 50K+ events/min.
     *
     * @param events List of parsed log events
     */
    public void bulkIndex(List<LogEvent> events) throws IOException {
        if (events.isEmpty()) return;

        BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();

        for (LogEvent event : events) {
            String indexName = getIndexName(event);
            bulkBuilder.operations(op -> op
                    .index(idx -> idx
                            .index(indexName)
                            .id(event.getId())
                            .document(event)
                    )
            );
        }

        BulkResponse response = esClient.bulk(bulkBuilder.build());

        if (response.errors()) {
            long errorCount = response.items().stream()
                    .filter(item -> item.error() != null)
                    .count();
            log.error("Bulk index had {} errors out of {} documents", errorCount, events.size());

            // Log specific errors for debugging
            response.items().stream()
                    .filter(item -> item.error() != null)
                    .limit(5)  // Log first 5 errors to avoid log spam
                    .forEach(item -> log.error("Index error for doc {}: {}",
                            item.id(), item.error().reason()));
        } else {
            log.debug("Successfully bulk indexed {} log events", events.size());
        }
    }

    /**
     * Generate time-based index name.
     * Example: logs-2024-01-15
     *
     * Time-based indices enable:
     * - Easy deletion of old data (delete index rather than individual docs)
     * - Smaller, faster per-day indices
     * - ILM (Index Lifecycle Management) policy application
     */
    private String getIndexName(LogEvent event) {
        LocalDate date;
        if (event.getTimestamp() != null) {
            date = event.getTimestamp().atOffset(ZoneOffset.UTC).toLocalDate();
        } else {
            date = LocalDate.now(ZoneOffset.UTC);
        }
        return indexPrefix + "-" + date.format(DATE_FORMATTER);
    }
}
