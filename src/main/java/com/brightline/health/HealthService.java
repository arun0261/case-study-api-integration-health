package com.brightline.health;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Service
public class HealthService {
    static final double WARNING_ERROR_RATE_PCT = 10.0;
    static final double CRITICAL_ERROR_RATE_PCT = 25.0;
    static final double WARNING_P95_LATENCY_MS = 1_000.0;
    static final int MIN_REQUESTS_FOR_HEALTH = 5;

    private final ObjectMapper objectMapper;
    private final Path logsPath;

    public HealthService(
            ObjectMapper objectMapper,
            @Value("${health.logs-path:starter_kit/data/integration_logs.json}") String logsPath) {
        this.objectMapper = objectMapper;
        this.logsPath = Path.of(logsPath);
    }

    public HealthReport report(int windowMinutes) {
        if (windowMinutes <= 0) {
            throw new IllegalArgumentException("windowMinutes must be greater than zero");
        }
        return summarize(loadLogs(), windowMinutes);
    }

    List<RequestLog> loadLogs() {
        JsonNode root;
        try {
            root = objectMapper.readTree(Files.readString(logsPath));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read integration logs from " + logsPath, exception);
        }
        JsonNode entries = root == null ? null : root.get("logs");
        if (entries == null || !entries.isArray()) {
            throw new IllegalStateException("Integration log file must contain a 'logs' array");
        }

        List<RequestLog> logs = new ArrayList<>();
        for (int index = 0; index < entries.size(); index++) {
            JsonNode entry = entries.get(index);
            try {
                String integration = requiredText(entry, "integration");
                String status = requiredText(entry, "status");
                LocalDateTime timestamp = LocalDateTime.parse(requiredText(entry, "timestamp"));
                JsonNode latencyNode = entry.get("latency_ms");
                if (latencyNode == null || !latencyNode.isNumber()) {
                    throw new IllegalArgumentException("latency_ms must be numeric");
                }
                double latency = latencyNode.doubleValue();
                if (!Double.isFinite(latency) || latency < 0) {
                    throw new IllegalArgumentException("latency_ms must be finite and non-negative");
                }
                logs.add(new RequestLog(integration, timestamp, status, latency));
            } catch (RuntimeException exception) {
                throw new IllegalStateException(
                        "Invalid integration log at logs[" + index + "]: " + exception.getMessage(), exception);
            }
        }
        return logs;
    }

    private String requiredText(JsonNode entry, String field) {
        JsonNode value = entry == null ? null : entry.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException(field + " must be a non-empty string");
        }
        return value.asText();
    }

    HealthReport summarize(List<RequestLog> logs, int windowMinutes) {
        if (windowMinutes <= 0) {
            throw new IllegalArgumentException("windowMinutes must be greater than zero");
        }
        if (logs.isEmpty()) {
            return new HealthReport(null, windowMinutes, "NO DATA", List.of());
        }

        LocalDateTime asOf = logs.stream().map(RequestLog::timestamp).max(Comparator.naturalOrder()).orElseThrow();
        LocalDateTime cutoff = asOf.minus(windowMinutes, ChronoUnit.MINUTES);
        List<RequestLog> recent = logs.stream()
                .filter(log -> !log.timestamp().isBefore(cutoff) && !log.timestamp().isAfter(asOf))
                .toList();
        TreeSet<String> integrations = logs.stream()
                .map(RequestLog::integration)
                .collect(Collectors.toCollection(TreeSet::new));
        List<IntegrationHealth> summaries = integrations.stream()
                .map(name -> summarizeIntegration(name, recent.stream()
                        .filter(log -> log.integration().equals(name))
                        .toList(), windowMinutes))
                .toList();

        return new HealthReport(asOf, windowMinutes, overallStatus(summaries), summaries);
    }

    private IntegrationHealth summarizeIntegration(
            String name, List<RequestLog> logs, int windowMinutes) {
        if (logs.isEmpty()) {
            return new IntegrationHealth(
                    name, "NO DATA", 0, 0, null, null, Map.of(),
                    "No requests in the last " + windowMinutes + " minutes.");
        }

        List<RequestLog> failures = logs.stream()
                .filter(log -> !log.status().equalsIgnoreCase("success"))
                .toList();
        List<Double> successfulLatencies = logs.stream()
                .filter(log -> log.status().equalsIgnoreCase("success"))
                .map(RequestLog::latencyMs)
                .toList();
        double errorRate = failures.size() * 100.0 / logs.size();
        Double p95Latency = percentile(successfulLatencies, 95);
        Map<String, Integer> failureBreakdown = new TreeMap<>();
        failures.forEach(log -> failureBreakdown.merge(log.status().toLowerCase(), 1, Integer::sum));
        List<String> reasons = new ArrayList<>();

        String status;
        if (logs.size() < MIN_REQUESTS_FOR_HEALTH) {
            status = "INSUFFICIENT DATA";
            reasons.add("Only " + logs.size() + " request(s); at least "
                    + MIN_REQUESTS_FOR_HEALTH + " are needed for a health classification.");
        } else if (errorRate >= CRITICAL_ERROR_RATE_PCT) {
            status = "UNHEALTHY";
            reasons.add(String.format("Failure rate %.1f%% is at or above %.0f%%.",
                    errorRate, CRITICAL_ERROR_RATE_PCT));
        } else if (errorRate >= WARNING_ERROR_RATE_PCT) {
            status = "DEGRADED";
            reasons.add(String.format("Failure rate %.1f%% is at or above %.0f%%.",
                    errorRate, WARNING_ERROR_RATE_PCT));
        } else {
            status = "HEALTHY";
            reasons.add(String.format("Failure rate %.1f%% is below %.0f%%.",
                    errorRate, WARNING_ERROR_RATE_PCT));
        }

        if (p95Latency != null && p95Latency >= WARNING_P95_LATENCY_MS) {
            if (status.equals("HEALTHY")) {
                status = "DEGRADED";
            }
            reasons.add(String.format("Successful-request p95 latency %.0f ms is at or above %.0f ms.",
                    p95Latency, WARNING_P95_LATENCY_MS));
        }
        if (!failureBreakdown.isEmpty()) {
            String breakdown = failureBreakdown.entrySet().stream()
                    .map(entry -> entry.getValue() + " " + entry.getKey())
                    .collect(Collectors.joining(", "));
            reasons.add("Failures: " + breakdown + ".");
        }

        return new IntegrationHealth(
                name, status, logs.size(), failures.size(), errorRate, p95Latency,
                Map.copyOf(failureBreakdown), String.join(" ", reasons));
    }

    private Double percentile(List<Double> values, int percentileRank) {
        if (values.isEmpty()) {
            return null;
        }
        List<Double> ordered = values.stream().sorted().toList();
        int rank = (int) Math.ceil(percentileRank / 100.0 * ordered.size());
        return ordered.get(rank - 1);
    }

    private String overallStatus(List<IntegrationHealth> summaries) {
        if (summaries.stream().anyMatch(item -> item.status().equals("UNHEALTHY"))) {
            return "UNHEALTHY";
        }
        if (summaries.stream().anyMatch(item -> item.status().equals("DEGRADED"))) {
            return "DEGRADED";
        }
        if (summaries.stream().anyMatch(item -> item.status().equals("NO DATA"))) {
            return "NO DATA";
        }
        if (summaries.stream().anyMatch(item -> item.status().equals("INSUFFICIENT DATA"))) {
            return "INSUFFICIENT DATA";
        }
        return summaries.isEmpty() ? "NO DATA" : "HEALTHY";
    }
}
