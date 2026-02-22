package com.logSearch.ingestion.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.logSearch.common.dto.LogIngestionRequest;
import com.logSearch.common.model.LogEvent;
import com.logSearch.ingestion.service.LogIngestionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(LogIngestionController.class)
@DisplayName("LogIngestionController")
class LogIngestionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private LogIngestionService ingestionService;

    @Test
    @DisplayName("POST /api/v1/logs returns 202 for valid single log")
    void ingestSingleLog_ReturnsAccepted() throws Exception {
        LogIngestionRequest request = LogIngestionRequest.builder()
                .serviceName("test-service")
                .log("INFO: User login successful")
                .host("prod-node-01")
                .build();

        LogEvent mockEvent = LogEvent.builder()
                .id(UUID.randomUUID().toString())
                .ingestedAt(Instant.now())
                .build();
        when(ingestionService.ingestSingle(any())).thenReturn(mockEvent);

        mockMvc.perform(post("/api/v1/logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("accepted"))
                .andExpect(jsonPath("$.eventId").exists());
    }

    @Test
    @DisplayName("POST /api/v1/logs returns 400 when 'log' is missing")
    void ingestSingleLog_ReturnsBadRequestWhenLogMissing() throws Exception {
        LogIngestionRequest request = LogIngestionRequest.builder()
                .serviceName("test-service")
                // log field intentionally omitted
                .build();

        mockMvc.perform(post("/api/v1/logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/logs returns 400 when serviceName is missing")
    void ingestSingleLog_ReturnsBadRequestWhenServiceNameMissing() throws Exception {
        LogIngestionRequest request = LogIngestionRequest.builder()
                .log("INFO: Some log")
                // serviceName intentionally omitted
                .build();

        mockMvc.perform(post("/api/v1/logs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/logs/batch returns 202 for valid batch")
    void ingestBatch_ReturnsAccepted() throws Exception {
        LogIngestionRequest request = LogIngestionRequest.builder()
                .serviceName("test-service")
                .logs(List.of(
                        "INFO: Request received",
                        "DEBUG: Processing request",
                        "INFO: Request completed in 45ms"
                ))
                .build();

        List<LogEvent> mockEvents = List.of(
                LogEvent.builder().id(UUID.randomUUID().toString()).build(),
                LogEvent.builder().id(UUID.randomUUID().toString()).build(),
                LogEvent.builder().id(UUID.randomUUID().toString()).build()
        );
        when(ingestionService.ingestBatch(any())).thenReturn(mockEvents);

        mockMvc.perform(post("/api/v1/logs/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("accepted"))
                .andExpect(jsonPath("$.acceptedCount").value(3));
    }

    @Test
    @DisplayName("GET /api/v1/logs/health returns UP")
    void health_ReturnsUp() throws Exception {
        mockMvc.perform(get("/api/v1/logs/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
