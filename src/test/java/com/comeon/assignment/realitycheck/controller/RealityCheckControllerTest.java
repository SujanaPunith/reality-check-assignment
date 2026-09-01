package com.comeon.assignment.realitycheck.controller;

import com.comeon.assignment.realitycheck.exception.RealityCheckException;
import com.comeon.assignment.realitycheck.exception.RealityCheckExceptionHandler;
import com.comeon.assignment.realitycheck.model.AcknowledgementResponse;
import com.comeon.assignment.realitycheck.model.RealityCheckSessionResponse;
import com.comeon.assignment.realitycheck.service.RealityCheckService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RealityCheckControllerTest {

    private RealityCheckService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(RealityCheckService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new RealityCheckController(service))
                .setControllerAdvice(new RealityCheckExceptionHandler())
                .build();
    }

    @Test
    void createsSessionWithFormattedTimestamps() throws Exception {
        when(service.createSession(1001, 10)).thenReturn(session("ACTIVE"));

        mockMvc.perform(post("/api/v1/players/1001/reality-check-session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"intervalMinutes\":10}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.intervalMinutes").value(10))
                .andExpect(jsonPath("$.startedAt").value("6 July 26 14:35"))
                .andExpect(jsonPath("$.nextCheckAt").value("6 July 26 14:45"));
    }

    @Test
    void returnsStructuredErrors() throws Exception {
        when(service.createSession(1001, 10))
                .thenThrow(new RealityCheckException("ACTIVE_SESSION_ALREADY_EXISTS"));

        mockMvc.perform(post("/api/v1/players/1001/reality-check-session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"intervalMinutes\":10}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACTIVE_SESSION_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void getsSessionAndMapsMissingResourcesToNotFound() throws Exception {
        when(service.getActiveSessionResponse(1001)).thenReturn(session("ACTIVE"));
        when(service.getActiveSessionResponse(1002))
                .thenThrow(new RealityCheckException("PLAYER_NOT_FOUND"));
        when(service.getActiveSessionResponse(1003))
                .thenThrow(new RealityCheckException("ACTIVE_SESSION_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/players/1001/reality-check-session"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/players/1002/reality-check-session"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLAYER_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/players/1003/reality-check-session"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACTIVE_SESSION_NOT_FOUND"));
    }

    @Test
    void updatesStopsAndAcknowledgesSessions() throws Exception {
        when(service.updateActiveSession(1001, 30)).thenReturn(session("ACTIVE"));
        when(service.stopActiveSession(1001)).thenReturn(session("STOPPED"));
        when(service.acknowledge(1001)).thenReturn(new AcknowledgementResponse(session("ACTIVE")));

        mockMvc.perform(patch("/api/v1/players/1001/reality-check-session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"intervalMinutes\":30}"))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/players/1001/reality-check-session"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("STOPPED"));
        mockMvc.perform(post("/api/v1/players/1001/reality-check-session/acknowledgements"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.session.acknowledgedAt").value("6 July 26 14:36"));
    }

    @Test
    void rejectsInvalidIntervals() throws Exception {
        mockMvc.perform(patch("/api/v1/players/1001/reality-check-session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"intervalMinutes\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INTERVAL_MINUTES"))
                .andExpect(jsonPath("$.message").value("intervalMinutes must be between 1 and 1440."));

        mockMvc.perform(post("/api/v1/players/1001/reality-check-session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"intervalMinutes\":1441}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INTERVAL_MINUTES"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    private static RealityCheckSessionResponse session(String status) {
        return new RealityCheckSessionResponse(
                1, 1001, 10, status, 10,
                "6 July 26 14:35", "6 July 26 14:35", "6 July 26 14:36",
                "6 July 26 14:45", 60, -4200);
    }
}
