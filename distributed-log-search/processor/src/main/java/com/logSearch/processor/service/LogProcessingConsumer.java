package com.logSearch.processor.service;

import com.logSearch.common.model.LogEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Kafka consumer that drives the log processing pipeline.
 *
 * Processing pipeline per batch:
 * 1. Receive batch of raw LogEvents from Kafka
 * 2. Parse each event (extract level, timestamp, fields)
 * 3. Enrich with metadata (partition, offset info)
 * 4. Bulk-index all events into Elasticsearch
 * 5. Acknowledge Kafka offset (commits consumed position)
 *
 * Fault tolerance:
 * - If indexing fails, the offset is NOT committed
 * - Kafka will redeliver the batch on next poll
 * - Dead Letter Topic (DLT) captures permanently failed messages
 */
@Slf4j
@Service
public class LogProcessingConsumer {

    private final LogParserService parserService;
    private final ElasticsearchIndexingService indexingService;
    private final Counter processedCounter;
    private final Counter failedCounter;
    private final Timer processingTimer;

    public LogProcessingConsumer(
            LogParserService parserService,
            ElasticsearchIndexingService indexingService,
            MeterRegistry meterRegistry) {
        this.parserService = parserService;
        this.indexingService = indexingService;
        this.processedCounter = Counter.builder("logs.processed.total")
                .description("Total log events processed and indexed")
                .register(meterRegistry);
        this.failedCounter = Counter.builder("logs.processed.failed")
                .description("Total log events that failed processing")
                .register(meterRegistry);
        this.processingTimer = Timer.builder("logs.processing.duration")
                .description("Time taken to process and index a batch of log events")
                .register(meterRegistry);
    }

    /**
     * Main Kafka listener. Processes batches of log events.
     *
     * Concurrency is configured in KafkaConsumerConfig (default: 3 threads).
     * Each thread handles its assigned partitions independently.
     */
    @KafkaListener(
            topics = "${kafka.topic.logs:log-events}",
            containerFactory = "kafkaListenerContainerFactory",
            groupId = "${kafka.consumer.group-id:log-processors}"
    )
    public void consume(List<ConsumerRecord<String, LogEvent>> records, Acknowledgment acknowledgment) {
        if (records.isEmpty()) {
            acknowledgment.acknowledge();
            return;
        }

        log.debug("Processing batch of {} log events", records.size());

        processingTimer.record(() -> {
            try {
                List<LogEvent> parsedEvents = parseBatch(records);
                indexingService.bulkIndex(parsedEvents);
                processedCounter.increment(parsedEvents.size());

                // Commit offset ONLY after successful indexing
                acknowledgment.acknowledge();
                log.debug("Successfully processed and indexed {} events", parsedEvents.size());

            } catch (IOException e) {
                failedCounter.increment(records.size());
                log.error("Failed to index batch of {} events: {}. Batch will be retried.",
                        records.size(), e.getMessage(), e);
                // Do NOT acknowledge — Kafka will redeliver this batch
                throw new RuntimeException("Elasticsearch indexing failed", e);

            } catch (Exception e) {
                failedCounter.increment(records.size());
                log.error("Unexpected error processing batch: {}", e.getMessage(), e);
                throw e;
            }
        });
    }

    /**
     * Parse all events in a batch.
     * Failures on individual events are logged but don't fail the whole batch.
     */
    private List<LogEvent> parseBatch(List<ConsumerRecord<String, LogEvent>> records) {
        List<LogEvent> parsed = new ArrayList<>(records.size());

        for (ConsumerRecord<String, LogEvent> record : records) {
            try {
                LogEvent event = record.value();
                if (event == null) {
                    log.warn("Received null event at partition {} offset {}", record.partition(), record.offset());
                    continue;
                }

                // Enrich with Kafka metadata before parsing
                event = event.toBuilder()
                        .partition(record.partition())
                        .offset(record.offset())
                        .build();

                // Parse the raw log string into structured fields
                LogEvent parsedEvent = parserService.parse(event);
                parsed.add(parsedEvent);

            } catch (Exception e) {
                log.error("Failed to parse record at partition {} offset {}: {}",
                        record.partition(), record.offset(), e.getMessage());
                // Include unparsed event rather than losing it
                if (record.value() != null) {
                    parsed.add(record.value());
                }
            }
        }

        return parsed;
    }
}
