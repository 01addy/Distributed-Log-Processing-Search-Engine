package com.logSearch.processor.service;

import com.logSearch.common.model.LogEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LogParserService")
class LogParserServiceTest {

    private LogParserService parser;

    @BeforeEach
    void setUp() {
        parser = new LogParserService();
    }

    private LogEvent buildRawEvent(String raw) {
        return LogEvent.builder()
                .id(UUID.randomUUID().toString())
                .rawLog(raw)
                .serviceName("test-service")
                .ingestedAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("parses Logback format correctly")
    void parsesLogbackFormat() {
        String raw = "2024-01-15 10:23:45.123 [main] ERROR com.example.PaymentService - Payment failed: timeout";
        LogEvent result = parser.parse(buildRawEvent(raw));

        assertThat(result.getLevel()).isEqualTo(LogEvent.LogLevel.ERROR);
        assertThat(result.getThreadName()).isEqualTo("main");
        assertThat(result.getLogger()).isEqualTo("com.example.PaymentService");
        assertThat(result.getMessage()).isEqualTo("Payment failed: timeout");
        assertThat(result.getTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("parses Log4j format correctly")
    void parsesLog4jFormat() {
        String raw = "2024-01-15 10:23:45,123 WARN [DatabasePool] - Connection pool at 80% capacity";
        LogEvent result = parser.parse(buildRawEvent(raw));

        assertThat(result.getLevel()).isEqualTo(LogEvent.LogLevel.WARN);
        assertThat(result.getMessage()).isEqualTo("Connection pool at 80% capacity");
        assertThat(result.getTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("parses JSON format correctly")
    void parsesJsonFormat() {
        String raw = """
                {"timestamp":"2024-01-15T10:23:45.123Z","level":"INFO","message":"User logged in","logger_name":"com.example.AuthService","thread_name":"http-nio-8080-exec-1"}
                """.trim();
        LogEvent result = parser.parse(buildRawEvent(raw));

        assertThat(result.getLevel()).isEqualTo(LogEvent.LogLevel.INFO);
        assertThat(result.getMessage()).isEqualTo("User logged in");
        assertThat(result.getLogger()).isEqualTo("com.example.AuthService");
    }

    @Test
    @DisplayName("parses plaintext format correctly")
    void parsesPlaintextFormat() {
        String raw = "ERROR: Database connection refused";
        LogEvent result = parser.parse(buildRawEvent(raw));

        assertThat(result.getLevel()).isEqualTo(LogEvent.LogLevel.ERROR);
        assertThat(result.getMessage()).isEqualTo("Database connection refused");
    }

    @ParameterizedTest
    @CsvSource({
            "TRACE",
            "DEBUG",
            "INFO",
            "WARN",
            "ERROR",
            "FATAL"
    })
    @DisplayName("correctly maps all log levels")
    void mapsAllLogLevels(String level) {
        String raw = level + ": Some message";
        LogEvent result = parser.parse(buildRawEvent(raw));
        assertThat(result.getLevel().name()).isEqualTo(level);
    }

    @Test
    @DisplayName("extracts trace ID from log message")
    void extractsTraceId() {
        String raw = "INFO User request processed traceId=a1b2c3d4e5f67890 in 45ms";
        LogEvent result = parser.parse(buildRawEvent(raw));
        assertThat(result.getTraceId()).isEqualTo("a1b2c3d4e5f67890");
    }

    @Test
    @DisplayName("handles null raw log gracefully")
    void handlesNullRawLog() {
        LogEvent event = buildRawEvent(null);
        LogEvent result = parser.parse(event);
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("handles empty log gracefully")
    void handlesEmptyLog() {
        LogEvent event = buildRawEvent("  ");
        LogEvent result = parser.parse(event);
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("extracts stack trace from error logs")
    void extractsStackTrace() {
        String raw = """
                ERROR Payment processing failed
                \tat com.example.PaymentService.process(PaymentService.java:45)
                \tat com.example.PaymentController.pay(PaymentController.java:23)
                """;
        LogEvent result = parser.parse(buildRawEvent(raw));
        assertThat(result.getStackTrace()).contains("at com.example.PaymentService");
    }

    @Test
    @DisplayName("sets UNKNOWN level for unrecognized format")
    void setsUnknownLevelForUnrecognizedFormat() {
        String raw = "some completely unstructured log message without a level";
        LogEvent result = parser.parse(buildRawEvent(raw));
        assertThat(result.getLevel()).isEqualTo(LogEvent.LogLevel.UNKNOWN);
    }

    @Test
    @DisplayName("preserves service name from original event")
    void preservesServiceName() {
        LogEvent event = buildRawEvent("INFO: test message");
        event = event.toBuilder().serviceName("my-service").build();
        LogEvent result = parser.parse(event);
        assertThat(result.getServiceName()).isEqualTo("my-service");
    }
}
