package com.logSearch.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LogIngestionRequest {

    @NotBlank(message = "Service name is required")
    private String serviceName;

    // Single log entry
    private String log;

    // Batch of log entries
    private List<String> logs;

    // Host from which logs are being sent
    private String host;

    // Optional format hint: JSON, LOGFMT, PLAINTEXT, LOG4J
    private String format;

    // Additional metadata tags 
    private Map<String, String> tags;
}
