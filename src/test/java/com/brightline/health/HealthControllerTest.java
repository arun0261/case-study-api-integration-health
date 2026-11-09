package com.brightline.health;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HealthControllerTest {
    private final TestHealthService healthService = new TestHealthService();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new HealthController(healthService)).build();
    }

    @Test
    void defaultsToSixtyMinuteWindow() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.windowMinutes").value(60))
                .andExpect(jsonPath("$.overallStatus").value("HEALTHY"))
                .andExpect(jsonPath("$.integrations[0].integration").value("Payroll"));

        assertEquals(60, healthService.lastWindowMinutes);
    }

    @Test
    void acceptsRequestedWindow() throws Exception {
        mockMvc.perform(get("/api/health").param("windowMinutes", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.windowMinutes").value(30));

        assertEquals(30, healthService.lastWindowMinutes);
    }

    @Test
    void returnsBadRequestForInvalidWindow() throws Exception {
        healthService.failure = new IllegalArgumentException("windowMinutes must be greater than zero");

        mockMvc.perform(get("/api/health").param("windowMinutes", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reportsLogDataErrorsAsInternalServerErrors() throws Exception {
        healthService.failure = new IllegalStateException("Invalid integration log");

        mockMvc.perform(get("/api/health"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Invalid integration log"));
    }

    private static class TestHealthService extends HealthService {
        private int lastWindowMinutes;
        private RuntimeException failure;

        private TestHealthService() {
            super(new ObjectMapper(), "unused");
        }

        @Override
        public HealthReport report(int windowMinutes) {
            lastWindowMinutes = windowMinutes;
            if (failure != null) {
                throw failure;
            }

            IntegrationHealth integration = new IntegrationHealth(
                    "Payroll", "HEALTHY", 5, 0, 0.0, 120.0, Map.of(),
                    "Failure rate 0.0% is below 10%.");
            return new HealthReport(
                    LocalDateTime.parse("2026-08-04T09:00:00"),
                    windowMinutes, "HEALTHY", List.of(integration));
        }
    }
}
