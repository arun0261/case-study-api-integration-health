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

    @Test
    void includesCutoffTimestampAndExcludesEarlierRequests() {
        LocalDateTime asOf = LocalDateTime.parse("2026-08-04T10:00:00");
        List<RequestLog> logs = new ArrayList<>();
        logs.add(log("Payroll", "error", asOf.minusMinutes(31)));
        logs.add(log("Payroll", "success", asOf.minusMinutes(30)));
        for (int index = 1; index < 4; index++) {
            logs.add(log("Payroll", "success", asOf.minusMinutes(30 - index * 5L)));
        }
        logs.add(log("Payroll", "success", asOf));

        IntegrationHealth result = service.summarize(logs, 30).integrations().get(0);

        assertEquals(5, result.requests());
        assertEquals(0, result.failures());
        assertEquals("HEALTHY", result.status());
    }

    @Test
    void classifiesExactFailureRateThresholds() {
        IntegrationHealth degraded = service.summarize(logsWithFailures(10, 1), 60)
                .integrations().get(0);
        IntegrationHealth unhealthy = service.summarize(logsWithFailures(8, 2), 60)
                .integrations().get(0);

        assertEquals(10.0, degraded.errorRatePct());
        assertEquals("DEGRADED", degraded.status());
        assertEquals(25.0, unhealthy.errorRatePct());
        assertEquals("UNHEALTHY", unhealthy.status());
    }

    @Test
    void successfulRequestP95AtThresholdDegradesIntegration() {
        List<RequestLog> logs = new ArrayList<>();
        for (int index = 0; index < 5; index++) {
            logs.add(new RequestLog(
                    "Payroll", LocalDateTime.parse("2026-08-04T09:00:00"),
                    "success", 1_000));
        }

        IntegrationHealth result = service.summarize(logs, 60).integrations().get(0);

        assertEquals(1_000.0, result.successP95LatencyMs());
        assertEquals("DEGRADED", result.status());
    }

    @Test
    void successStatusIsCaseInsensitive() {
        List<RequestLog> logs = List.of(
                log("Payroll", "SUCCESS", LocalDateTime.parse("2026-08-04T09:00:00")),
                log("Payroll", "success", LocalDateTime.parse("2026-08-04T09:01:00")),
                log("Payroll", "success", LocalDateTime.parse("2026-08-04T09:02:00")),
                log("Payroll", "success", LocalDateTime.parse("2026-08-04T09:03:00")),
                log("Payroll", "success", LocalDateTime.parse("2026-08-04T09:04:00")));

        IntegrationHealth result = service.summarize(logs, 60).integrations().get(0);

        assertEquals(0, result.failures());
        assertEquals("HEALTHY", result.status());
    }

    private List<RequestLog> logsWithFailures(int requests, int failures) {
        List<RequestLog> logs = new ArrayList<>();
        LocalDateTime timestamp = LocalDateTime.parse("2026-08-04T09:00:00");
        for (int index = 0; index < requests; index++) {
            logs.add(log("Payroll", index < failures ? "error" : "success", timestamp.plusMinutes(index)));
        }
        return logs;
    }

    private RequestLog log(String integration, String status, LocalDateTime timestamp) {
        return new RequestLog(integration, timestamp, status, 120);
    }
}
