package com.logSearch.ingestion.config;

import com.logSearch.common.model.LogEvent;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka producer configuration for publishing log events to topics.
 *
 * Key design decisions:
 * - acks=all: Ensures durability (leader + all replicas acknowledge)
 * - enable.idempotence=true: Prevents duplicate messages on retries
 * - compression: Snappy for good CPU/throughput balance with log data
 * - batch.size + linger.ms: Tuned for high throughput (50K+ events/min)
 */
@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${kafka.producer.batch-size:65536}")
    private int batchSize;

    @Value("${kafka.producer.linger-ms:10}")
    private int lingerMs;

    @Value("${kafka.producer.buffer-memory:33554432}")
    private long bufferMemory;

    @Bean
    public ProducerFactory<String, LogEvent> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();

        // Bootstrap
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        // Serializers
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        // Reliability: all replicas must ack before success
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");

        // Idempotent producer prevents duplicates during retries
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

        // Retry on transient failures (network blips, leader elections)
        configProps.put(ProducerConfig.RETRIES_CONFIG, 5);
        configProps.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, 100);

        // Throughput tuning: larger batches = fewer requests
        configProps.put(ProducerConfig.BATCH_SIZE_CONFIG, batchSize);        // 64KB batches
        configProps.put(ProducerConfig.LINGER_MS_CONFIG, lingerMs);          // Wait up to 10ms for batch fill
        configProps.put(ProducerConfig.BUFFER_MEMORY_CONFIG, bufferMemory);  // 32MB send buffer

        // Snappy: good compression ratio, low CPU cost — ideal for log data
        configProps.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");

        // Delivery timeout: 2 minutes before giving up
        configProps.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 120000);
        configProps.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 30000);

        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    public KafkaTemplate<String, LogEvent> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}
