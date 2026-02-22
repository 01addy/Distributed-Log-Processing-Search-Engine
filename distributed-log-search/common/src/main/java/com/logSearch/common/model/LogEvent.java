package com.logSearch.common.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;


@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class LogEvent {

    private String id;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant timestamp;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant ingestedAt;

    private LogLevel level;

    private String message;

    private String serviceName;

    private String host;

    private String traceId;

    private String spanId;

    private String threadName;

    private String logger;

    private String stackTrace;

    private Integer partition;

    private Long offset;

    private Map<String, String> metadata;

    private String rawLog;

    public static LogEvent create(String rawLog, String serviceName) {
        return LogEvent.builder()
                .id(UUID.randomUUID().toString())
                .rawLog(rawLog)
                .serviceName(serviceName)
                .ingestedAt(Instant.now())
                .build();
    }

    public enum LogLevel {
        TRACE, DEBUG, INFO, WARN, ERROR, FATAL, UNKNOWN
    }
}
