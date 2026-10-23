# API Integration Health — Solution Design

## Problem and v1 outcome

Brightline HR's operations team needs a morning view that identifies integrations requiring attention and explains why, instead of discovering failures through customer tickets and manually searching logs. The starter dataset contains 60 events for five integrations over 55 minutes. In particular, Greenhouse ATS Sync records `timeout` separately from `error`; excluding timeouts would make its reported health materially misleading.

Version 1 is a Java Spring Boot API with an Angular operations dashboard. The API evaluates the supplied file and exposes a structured report; the dashboard makes the fleet-level status, per-integration evidence, and reasons immediately visible. The health policy remains deterministic and inspectable rather than hidden in a black-box score.

## Assumptions and policy

- A request is successful only when `status` is `success`; every other non-empty status (including timeout) is a failed request. Failure rate is failed requests divided by all requests in the selected window.
- Reports use a trailing 60-minute window by default. The evaluation time is the latest timestamp in the input, rather than the machine clock, so historical files are reproducible. The cutoff and latest event are included.
- At least five requests are required to classify health. Lower volume is reported as `INSUFFICIENT DATA`, not implicitly healthy.
- With sufficient volume, failure rate below 10% is healthy, 10% to below 25% is degraded, and 25% or more is unhealthy. These thresholds flag a persistent pattern without making one failure in the sample appear catastrophic; at the same time, a 25% failure rate is actionable.
- Successful-request p95 latency at or above 1,000 ms degrades an otherwise healthy integration. Failed and timed-out latencies are not mixed into successful latency because the error rate and failure breakdown already expose their impact.
- Overall health is the worst integration classification, rather than an average that could conceal a broken payroll or ATS integration behind healthy services. A missing integration cannot be inferred from request logs alone, so this report covers integrations present in the file and marks one with no in-window requests as `NO DATA`.
- The provided timestamps are timezone-naive and are treated as one consistent local/UTC-like timeline. Production ingestion should require and normalize UTC.

## Approach and trade-offs

The Java API validates the file shape and essential fields, filters the rolling window, groups events per integration, calculates failure rate and nearest-rank successful p95, and returns the status with a human-readable rationale. It also reports counts by failure status so an operator can distinguish timeouts from explicit errors. The Angular client presents the response and allows the operator to select a 30-, 60-, or 120-minute window.

The thresholds are intentionally simple and configurable by code rather than presented as learned truth. A short, volume-based window is easy to understand and responsive, but can be noisy at low traffic; the minimum sample guard makes uncertainty explicit. Worst-status rollup is conservative and clear, but does not express business criticality. Successful-only latency avoids timeout outliers dominating the latency percentile, but means timeout duration is represented through failure rate rather than p95.

On the seed dataset, the report marks ADP Payroll Sync, DocuSign Envelope API, and Greenhouse ATS Sync unhealthy, primarily due to high failure rates; the Greenhouse count includes five timeouts. Slack Notifications and Workday Directory Sync remain healthy with one failure each in twelve requests.

## Next steps with more time, data, or budget

1. Confirm severity targets with Ops and integration owners; tune alert thresholds from historical incident and SLO data, including per-provider rate limits and retry behavior.
2. Ingest logs continuously and evaluate fixed five-minute buckets plus a longer rolling baseline; alert on sustained failures, sudden changes, and stale/missing telemetry.
3. Maintain an expected-integration registry and attach customer/request impact and business criticality so absent traffic is not confused with healthy status.
4. Persist history, show trends and recent incidents in a small web dashboard, and integrate notifications with deduplication and recovery handling.
5. Add schema/version handling, timezone-aware timestamps, richer HTTP/exception dimensions, and operational tests against delayed, duplicated, and out-of-order events.

An LLM-generated incident summary could be added later as a presentation layer over these deterministic metrics. It should not decide health or replace the numeric evidence.
