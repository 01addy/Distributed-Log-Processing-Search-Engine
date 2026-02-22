package com.logSearch.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * DTO for incoming log ingestion requests from producers.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogIngestionRequest {

    @NotBlank(message = "Service name is required")
    private String serviceName;

    /** Single log entry (use this or logs, not both) */
    private String log;

    /** Batch of log entries for bulk ingestion */
    private List<String> logs;

    /** Host from which logs are being sent */
    private String host;

    /** Optional format hint: JSON, LOGFMT, PLAINTEXT, LOG4J */
    private String format;

    /** Additional metadata tags to attach to all logs in this request */
    private Map<String, String> tags;
}
