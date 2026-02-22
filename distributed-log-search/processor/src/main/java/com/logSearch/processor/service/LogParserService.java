package com.logSearch.processor.service;

import com.logSearch.common.model.LogEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses raw log strings into structured LogEvent objects.
 *
 * Supports 4 log formats:
 * 1. JSON  - {"level":"ERROR","message":"...","timestamp":"..."}
 * 2. Log4j - 2024-01-15 10:23:45,123 ERROR [ServiceName] - Message
 * 3. Logback - 2024-01-15 10:23:45.123 [thread] ERROR logger - Message
 * 4. Plaintext - ERROR: some message
 */
@Slf4j
@Service
public class LogParserService {

    // Log4j pattern: 2024-01-15 10:23:45,123 ERROR [ClassName] - Message
    private static final Pattern LOG4J_PATTERN = Pattern.compile(
            "^(\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}[,.]\\d{1,3})\\s+" +
            "(TRACE|DEBUG|INFO|WARN|ERROR|FATAL)\\s+" +
            "(?:\\[([^\\]]+)\\]\\s+)?-?\\s*(.+)$",
            Pattern.DOTALL
    );

    // Logback pattern: 2024-01-15 10:23:45.123 [thread-name] ERROR com.example.Class - Message
    private static final Pattern LOGBACK_PATTERN = Pattern.compile(
            "^(\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}\\.\\d{1,3})\\s+" +
            "\\[([^\\]]+)\\]\\s+" +
            "(TRACE|DEBUG|INFO|WARN|ERROR|FATAL)\\s+" +
            "([\\w.]+)\\s+-\\s+(.+)$",
            Pattern.DOTALL
    );

    // Simple plaintext: LEVEL: message or LEVEL message
    private static final Pattern PLAINTEXT_PATTERN = Pattern.compile(
            "^(TRACE|DEBUG|INFO|WARN|ERROR|FATAL):?\\s+(.+)$",
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE
    );

    // Stack trace detection
    private static final Pattern STACK_TRACE_PATTERN = Pattern.compile(
            "(\\w+Exception|Error|Throwable)[:\\s]"
    );

    // Trace ID extraction from logs
    private static final Pattern TRACE_ID_PATTERN = Pattern.compile(
            "(?:traceId|trace_id|X-Trace-Id)[=:\\s]+([a-f0-9]{16,32})",
            Pattern.CASE_INSENSITIVE
    );

    private static final List<DateTimeFormatter> TIMESTAMP_FORMATS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss,SSS"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss,SSS")
    );

    /**
     * Parse a raw log string into a structured LogEvent.
     * Attempts JSON parsing first, then format-specific patterns.
     *
     * @param event The LogEvent with rawLog set
     * @return The enriched LogEvent with parsed fields
     */
    public LogEvent parse(LogEvent event) {
        if (event.getRawLog() == null || event.getRawLog().isBlank()) {
            return event;
        }

        String raw = event.getRawLog().trim();
        LogEvent.LogEventBuilder builder = event.toBuilder();

        try {
            if (raw.startsWith("{")) {
                parseJson(raw, builder, event);
            } else if (LOGBACK_PATTERN.matcher(raw).matches()) {
                parseLogback(raw, builder);
            } else if (LOG4J_PATTERN.matcher(raw).matches()) {
                parseLog4j(raw, builder);
            } else {
                parsePlaintext(raw, builder);
            }

            // Common enrichment regardless of format
            enrichWithTraceId(raw, builder);
            enrichWithStackTrace(raw, builder);

        } catch (Exception e) {
            log.debug("Failed to parse log, using raw message: {}", e.getMessage());
            builder.message(raw);
            builder.level(LogEvent.LogLevel.UNKNOWN);
        }

        return builder.build();
    }

    /**
     * Parse JSON-formatted log (e.g., from logstash-logback-encoder).
     */
    private void parseJson(String raw, LogEvent.LogEventBuilder builder, LogEvent original) {
        try {
            // Simple JSON field extraction without full deserialization for performance
            builder.message(extractJsonField(raw, "message", "msg"));
            builder.level(parseLevel(extractJsonField(raw, "level", "severity", "log.level")));
            builder.threadName(extractJsonField(raw, "thread_name", "thread"));
            builder.logger(extractJsonField(raw, "logger_name", "logger", "class"));
            builder.traceId(extractJsonField(raw, "traceId", "trace_id", "X-Trace-Id"));
            builder.spanId(extractJsonField(raw, "spanId", "span_id"));

            String ts = extractJsonField(raw, "timestamp", "@timestamp", "time");
            if (ts != null) {
                builder.timestamp(parseTimestamp(ts));
            }
        } catch (Exception e) {
            builder.message(raw);
        }
    }

    /**
     * Parse Logback-formatted log.
     * Example: 2024-01-15 10:23:45.123 [main] ERROR com.example.Service - User not found
     */
    private void parseLogback(String raw, LogEvent.LogEventBuilder builder) {
        Matcher m = LOGBACK_PATTERN.matcher(raw);
        if (m.matches()) {
            builder.timestamp(parseTimestamp(m.group(1)));
            builder.threadName(m.group(2));
            builder.level(parseLevel(m.group(3)));
            builder.logger(m.group(4));
            builder.message(m.group(5).trim());
        }
    }

    /**
     * Parse Log4j-formatted log.
     * Example: 2024-01-15 10:23:45,123 ERROR [ServiceClass] - Connection refused
     */
    private void parseLog4j(String raw, LogEvent.LogEventBuilder builder) {
        Matcher m = LOG4J_PATTERN.matcher(raw);
        if (m.matches()) {
            builder.timestamp(parseTimestamp(m.group(1)));
            builder.level(parseLevel(m.group(2)));
            if (m.group(3) != null) builder.logger(m.group(3));
            builder.message(m.group(4).trim());
        }
    }

    /**
     * Parse plaintext log.
     * Example: ERROR: Database connection failed
     */
    private void parsePlaintext(String raw, LogEvent.LogEventBuilder builder) {
        Matcher m = PLAINTEXT_PATTERN.matcher(raw);
        if (m.matches()) {
            builder.level(parseLevel(m.group(1)));
            builder.message(m.group(2).trim());
        } else {
            builder.message(raw);
            builder.level(LogEvent.LogLevel.UNKNOWN);
        }
    }

    /**
     * Extract trace ID from log message for cross-service correlation.
     */
    private void enrichWithTraceId(String raw, LogEvent.LogEventBuilder builder) {
        Matcher m = TRACE_ID_PATTERN.matcher(raw);
        if (m.find()) {
            builder.traceId(m.group(1));
        }
    }

    /**
     * Detect and extract stack traces from error logs.
     */
    private void enrichWithStackTrace(String raw, LogEvent.LogEventBuilder builder) {
        if (STACK_TRACE_PATTERN.matcher(raw).find() && raw.contains("\n\tat ")) {
            // Extract stack trace portion
            int stackStart = raw.indexOf("\n\tat ");
            if (stackStart > 0) {
                String mainMessage = raw.substring(0, stackStart).trim();
                String stackTrace = raw.substring(stackStart).trim();
                builder.message(mainMessage);
                builder.stackTrace(stackTrace);

                // Override level to ERROR if we found a stack trace
                // (sometimes logs don't specify level but still contain exceptions)
            }
        }
    }

    private LogEvent.LogLevel parseLevel(String level) {
        if (level == null) return LogEvent.LogLevel.UNKNOWN;
        try {
            return LogEvent.LogLevel.valueOf(level.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            return LogEvent.LogLevel.UNKNOWN;
        }
    }

    private Instant parseTimestamp(String ts) {
        if (ts == null) return Instant.now();

        // Try ISO instant first
        try {
            return Instant.parse(ts);
        } catch (DateTimeParseException ignored) {}

        // Try common formats
        for (DateTimeFormatter fmt : TIMESTAMP_FORMATS) {
            try {
                return LocalDateTime.parse(ts, fmt).toInstant(ZoneOffset.UTC);
            } catch (DateTimeParseException ignored) {}
        }

        return Instant.now();
    }

    /**
     * Simple JSON field extractor — avoids heavyweight deserialization for single-field access.
     */
    private String extractJsonField(String json, String... fieldNames) {
        for (String field : fieldNames) {
            String searchKey = "\"" + field + "\"";
            int keyIdx = json.indexOf(searchKey);
            if (keyIdx == -1) continue;

            int colonIdx = json.indexOf(':', keyIdx + searchKey.length());
            if (colonIdx == -1) continue;

            int valueStart = colonIdx + 1;
            while (valueStart < json.length() && json.charAt(valueStart) == ' ') valueStart++;

            if (valueStart >= json.length()) continue;

            if (json.charAt(valueStart) == '"') {
                int valueEnd = json.indexOf('"', valueStart + 1);
                if (valueEnd > valueStart) {
                    return json.substring(valueStart + 1, valueEnd);
                }
            } else {
                int valueEnd = json.indexOf(',', valueStart);
                if (valueEnd == -1) valueEnd = json.indexOf('}', valueStart);
                if (valueEnd > valueStart) {
                    return json.substring(valueStart, valueEnd).trim();
                }
            }
        }
        return null;
    }
}
