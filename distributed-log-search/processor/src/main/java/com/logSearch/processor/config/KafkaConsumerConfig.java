package com.logSearch.processor.config;

import com.logSearch.common.model.LogEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka consumer configuration for the processor service.
 *
 * Design decisions:
 * - Consumer group: allows horizontal scaling of processors
 * - Manual ACK (MANUAL_IMMEDIATE): ensures messages are only committed after successful processing
 * - Concurrency: configurable parallel consumer threads
 * - Error handling: retry with backoff before sending to DLT
 */
@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${kafka.consumer.group-id:log-processors}")
    private String groupId;

    @Value("${kafka.consumer.concurrency:3}")
    private int concurrency;

    @Value("${kafka.consumer.max-poll-records:100}")
    private int maxPollRecords;

    @Bean
    public ConsumerFactory<String, LogEvent> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);

        // Batch polling for throughput
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, maxPollRecords);
        props.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, 1024);          // 1KB min fetch
        props.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 500);

        // Prevent consumer group rebalancing due to slow processing
        props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 300000);   // 5 min
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 30000);       // 30 sec
        props.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, 10000);    // 10 sec

        // Start from beginning for new consumer groups (useful in dev)
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");

        // Disable auto-commit — we use manual AFTER_PROCESSING acknowledgment
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        // Trust all packages from our model
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.logSearch.*");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, LogEvent.class.getName());

        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, LogEvent> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, LogEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(consumerFactory());

        // Number of concurrent consumer threads
        factory.setConcurrency(concurrency);

        // Batch listener for processing multiple records at once
        factory.setBatchListener(true);

        // Manual acknowledgment: commit offset only after successful processing
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);

        // Error handler: retry 3 times with 1s backoff, then send to dead-letter topic
        factory.setCommonErrorHandler(new DefaultErrorHandler(
                new FixedBackOff(1000L, 3L)  // 3 retries, 1s interval
        ));

        return factory;
    }
}
