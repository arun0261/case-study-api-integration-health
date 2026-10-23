# API Integration Health

An Angular operations dashboard backed by a Java Spring Boot API. It turns the provided request logs into an at-a-glance health report with status, request/failure counts, failure rate, successful-request p95 latency, failure-type breakdown, and an explanation for each classification.

## Requirements

- Java JDK 17 or newer
- Maven 3.9+
- Node.js 20.19+ (Node 20), 22.12+ (Node 22), or Node 24, with npm included

Ensure `java`, `mvn`, `node`, and `npm` are available in your terminal. Internet access is required on the first run to download Maven and npm dependencies.

## Sharing and running from a ZIP

Extract the ZIP before running commands. Open a terminal in the extracted project folder (the folder containing `pom.xml`), then follow the two-terminal instructions below. Keep both terminals running while using the dashboard.

Include the source code, `pom.xml`, the frontend configuration and `package-lock.json`, `starter_kit\data`, `README.md`, and `SOLUTION_DESIGN.md` in the ZIP. Exclude `.git`, `.idea`, `target`, `frontend\node_modules`, `frontend\dist`, and `frontend\.angular`; generated dependencies and build outputs are recreated locally.

This source ZIP requires the tools listed above. It is not a standalone executable or a combined Java JAR containing the Angular application.

## Run locally

Start the Java API from the repository root:

```powershell
mvn spring-boot:run
```

The API reads `starter_kit/data/integration_logs.json` and serves `GET http://localhost:8080/api/health?windowMinutes=60`. The optional `health.logs-path` Spring property can point to another compatible JSON file.

In a second terminal, start Angular:

```powershell
cd frontend
npm install
npm start
```

Open `http://localhost:4200`. Angular proxies `/api` requests to the Java service. The dashboard supports 30-, 60-, and 120-minute windows and can be built for production with `npm run build`.

## Tests

Run Java service tests from the repository root:

```powershell
mvn test
```

## Health policy

- The evaluation time is the newest event timestamp in the log file, so historical runs are deterministic. The rolling window includes the cutoff and latest event.
- Fewer than 5 requests is `INSUFFICIENT DATA`. At 5 or more requests, failure rate of at least 25% is `UNHEALTHY`; at least 10% is `DEGRADED`; lower rates are `HEALTHY`.
- Any status other than `success` is a failed request, including `timeout`.
- Successful-request p95 latency of at least 1,000 ms degrades an otherwise healthy integration.
- Overall health uses the worst classification; unhealthy and degraded states take precedence, followed by missing recent data and insufficient volume.

## Limitations

This is a batch report, not a continuously refreshed monitor. A production version should ingest events continuously, use UTC-aware timestamps, model expected integrations independently of observed log traffic, account for request volume and customer impact, expose alerting and history, and tune thresholds against service-level objectives and customer baselines. The seed data is synthetic and covers only about an hour, so these defaults are starting assumptions, not validated SLOs.
