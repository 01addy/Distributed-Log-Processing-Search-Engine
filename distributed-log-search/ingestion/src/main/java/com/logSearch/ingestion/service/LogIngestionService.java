package com.logSearch.ingestion.service;

import com.logSearch.common.dto.LogIngestionRequest;
import com.logSearch.common.model.LogEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Core ingestion service.
 *
 * Responsibilities:
 * - Accept single and batch log events
 * - Assign unique IDs and ingest timestamps
 * - Route logs to the correct Kafka partition (by service name)
 * - Handle backpressure by limiting batch sizes
 * - Expose metrics for monitoring
 */
@Slf4j
@Service
public class LogIngestionService {

    private static final int MAX_BATCH_SIZE = 1000;

    private final KafkaTemplate<String, LogEvent> kafkaTemplate;
    private final String kafkaTopic;
    private final Counter ingestedCounter;
    private final Counter failedCounter;
    private final Timer publishTimer;

    public LogIngestionService(
            KafkaTemplate<String, LogEvent> kafkaTemplate,
            @Value("${kafka.topic.logs:log-events}") String kafkaTopic,
            MeterRegistry meterRegistry) {
        this.kafkaTemplate = kafkaTemplate;
        this.kafkaTopic = kafkaTopic;
        this.ingestedCounter = Counter.builder("logs.ingested.total")
                .description("Total number of log events successfully ingested")
                .register(meterRegistry);
        this.failedCounter = Counter.builder("logs.ingested.failed")
                .description("Total number of log events that failed to ingest")
                .register(meterRegistry);
        this.publishTimer = Timer.builder("logs.kafka.publish.duration")
                .description("Time taken to publish events to Kafka")
                .register(meterRegistry);
    }

    /**
     * Ingest a single log event.
     *
     * @param request The ingestion request containing log data
     * @return The created LogEvent
     */
    public LogEvent ingestSingle(LogIngestionRequest request) {
        LogEvent event = buildLogEvent(request.getLog(), request);
        publishToKafka(event);
        return event;
    }

    /**
     * Ingest a batch of log events.
     * Enforces MAX_BATCH_SIZE to prevent overload.
     *
     * @param request The ingestion request containing multiple logs
     * @return List of created LogEvents
     */
    public List<LogEvent> ingestBatch(LogIngestionRequest request) {
        List<String> logs = request.getLogs();

        if (logs == null || logs.isEmpty()) {
            return List.of();
        }

        if (logs.size() > MAX_BATCH_SIZE) {
            log.warn("Batch size {} exceeds maximum {}. Truncating.", logs.size(), MAX_BATCH_SIZE);
            logs = logs.subList(0, MAX_BATCH_SIZE);
        }

        List<LogEvent> events = new ArrayList<>(logs.size());
        List<CompletableFuture<SendResult<String, LogEvent>>> futures = new ArrayList<>();

        for (String rawLog : logs) {
            LogEvent event = buildLogEvent(rawLog, request);
            events.add(event);

            // Publish asynchronously — collect futures for error tracking
            CompletableFuture<SendResult<String, LogEvent>> future = publishAsync(event);
            futures.add(future);
        }

        // Wait for all messages to be acknowledged by Kafka
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .exceptionally(ex -> {
                    log.error("Some messages in batch failed to publish", ex);
                    return null;
                }).join();

        log.debug("Ingested batch of {} events for service '{}'", events.size(), request.getServiceName());
        return events;
    }

    /**
     * Builds a LogEvent from raw log string and request metadata.
     */
    private LogEvent buildLogEvent(String rawLog, LogIngestionRequest request) {
        return LogEvent.builder()
                .id(UUID.randomUUID().toString())
                .rawLog(rawLog)
                .serviceName(request.getServiceName())
                .host(request.getHost())
                .ingestedAt(Instant.now())
                .timestamp(Instant.now()) // Will be overwritten by processor if parseable
                .level(LogEvent.LogLevel.UNKNOWN) // Will be set by processor
                .message(rawLog) // Raw message until processed
                .metadata(request.getTags())
                .build();
    }

    /**
     * Synchronously publishes a log event to Kafka.
     * Uses service name as the partition key to ensure ordering per service.
     */
    private void publishToKafka(LogEvent event) {
        publishTimer.record(() -> {
            try {
                kafkaTemplate.send(kafkaTopic, event.getServiceName(), event).get();
                ingestedCounter.increment();
                log.debug("Published event {} to topic {}", event.getId(), kafkaTopic);
            } catch (Exception e) {
                failedCounter.increment();
                log.error("Failed to publish event {} to Kafka: {}", event.getId(), e.getMessage(), e);
                throw new RuntimeException("Failed to publish log event to Kafka", e);
            }
        });
    }

    /**
     * Asynchronously publishes a log event to Kafka.
     * Used for batch ingestion to maximize throughput.
     */
    private CompletableFuture<SendResult<String, LogEvent>> publishAsync(LogEvent event) {
        CompletableFuture<SendResult<String, LogEvent>> future =
                kafkaTemplate.send(kafkaTopic, event.getServiceName(), event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                failedCounter.increment();
                log.error("Failed to publish event {}: {}", event.getId(), ex.getMessage());
            } else {
                ingestedCounter.increment();
                log.debug("Published event {} to partition {} offset {}",
                        event.getId(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });

        return future;
    }
}
