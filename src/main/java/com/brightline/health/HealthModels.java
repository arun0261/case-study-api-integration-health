package com.brightline.health;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

record RequestLog(String integration, LocalDateTime timestamp, String status, double latencyMs) {}

record IntegrationHealth(
        String integration,
        String status,
        int requests,
        int failures,
        Double errorRatePct,
        Double successP95LatencyMs,
        Map<String, Integer> failureBreakdown,
        String reason) {}

record HealthReport(
        LocalDateTime asOf,
        int windowMinutes,
        String overallStatus,
        List<IntegrationHealth> integrations) {}
