package com.logSearch.queue.producer;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.TopicDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ExecutionException;

/**
 * Utility for managing Kafka topics programmatically.
 *
 * Usage:
 *   KafkaTopicAdmin admin = new KafkaTopicAdmin("localhost:9092");
 *   admin.createTopicIfNotExists("log-events", 6, 1);
 *   admin.describeTopics(List.of("log-events"));
 *   admin.close();
 */
public class KafkaTopicAdmin implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(KafkaTopicAdmin.class);
    private final AdminClient adminClient;

    public KafkaTopicAdmin(String bootstrapServers) {
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 30000);
        this.adminClient = AdminClient.create(props);
    }

    /**
     * Create a topic if it doesn't already exist.
     *
     * @param name              Topic name
     * @param numPartitions     Number of partitions (recommendation: match ES shard count)
     * @param replicationFactor Replication factor (1 for dev, 3 for production)
     */
    public void createTopicIfNotExists(String name, int numPartitions, short replicationFactor) {
        try {
            Set<String> existing = adminClient.listTopics().names().get();
            if (existing.contains(name)) {
                log.info("Topic '{}' already exists, skipping creation", name);
                return;
            }

            NewTopic topic = new NewTopic(name, numPartitions, replicationFactor);
            topic.configs(Map.of(
                    "retention.ms", String.valueOf(48 * 60 * 60 * 1000L),  // 48h retention
                    "segment.bytes", String.valueOf(1073741824L),            // 1GB segments
                    "cleanup.policy", "delete"
            ));

            adminClient.createTopics(List.of(topic)).all().get();
            log.info("Created topic '{}' with {} partitions and replication factor {}",
                    name, numPartitions, replicationFactor);

        } catch (ExecutionException | InterruptedException e) {
            log.error("Failed to create topic '{}': {}", name, e.getMessage(), e);
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Get partition and replica information for topics.
     */
    public Map<String, TopicDescription> describeTopics(List<String> topicNames) {
        try {
            return adminClient.describeTopics(topicNames).allTopicNames().get();
        } catch (ExecutionException | InterruptedException e) {
            log.error("Failed to describe topics: {}", e.getMessage());
            Thread.currentThread().interrupt();
            return Map.of();
        }
    }

    /**
     * Get consumer group lag per partition.
     * Useful for monitoring processing throughput.
     */
    public void printConsumerGroupLag(String groupId) {
        try {
            var offsets = adminClient.listConsumerGroupOffsets(groupId)
                    .partitionsToOffsetAndMetadata().get();

            log.info("Consumer group '{}' offsets:", groupId);
            offsets.forEach((tp, offset) ->
                    log.info("  {}-{}: offset={}", tp.topic(), tp.partition(), offset.offset())
            );
        } catch (ExecutionException | InterruptedException e) {
            log.error("Failed to get consumer group lag: {}", e.getMessage());
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        adminClient.close();
    }
}
