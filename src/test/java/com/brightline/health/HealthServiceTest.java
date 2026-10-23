package com.brightline.health;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HealthServiceTest {
    private final HealthService service = new HealthService(
            new ObjectMapper(), "starter_kit/data/integration_logs.json");

    @Test
    void seedDataIncludesTimeoutsInFailureRate() {
        HealthReport report = service.report(60);
        IntegrationHealth greenhouse = report.integrations().stream()
                .filter(item -> item.integration().equals("Greenhouse ATS Sync"))
                .findFirst()
                .orElseThrow();

        assertEquals("UNHEALTHY", report.overallStatus());
        assertEquals(6, greenhouse.failures());
        assertEquals(50.0, greenhouse.errorRatePct());
        assertEquals(5, greenhouse.failureBreakdown().get("timeout"));
    }

    @Test
    void excludesLogsOutsideRollingWindowAndUsesLatestTimestamp() {
        List<RequestLog> logs = new ArrayList<>();
        logs.add(log("Payroll", "error", LocalDateTime.parse("2026-08-04T08:00:00")));
        for (int index = 0; index < 5; index++) {
            logs.add(log("Payroll", "success",
                    LocalDateTime.parse("2026-08-04T09:00:00").plusMinutes(index * 5L)));
        }

        IntegrationHealth result = service.summarize(logs, 30).integrations().get(0);
        assertEquals("HEALTHY", result.status());
        assertEquals(5, result.requests());
        assertEquals(0, result.failures());
    }

    @Test
    void lowVolumeAndNoDataAreExplicit() {
        HealthReport sparse = service.summarize(
                List.of(log("New", "success", LocalDateTime.parse("2026-08-04T09:00:00"))), 60);
        assertEquals("INSUFFICIENT DATA", sparse.integrations().get(0).status());

        HealthReport noData = service.summarize(List.of(), 60);
        assertEquals("NO DATA", noData.overallStatus());
        assertNull(noData.asOf());
    }

    @Test
    void invalidWindowIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.report(0));
    }

    private RequestLog log(String integration, String status, LocalDateTime timestamp) {
        return new RequestLog(integration, timestamp, status, 120);
    }
}
