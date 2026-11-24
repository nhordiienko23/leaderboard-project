# Leaderboard Project

Event-driven microservices project built with Java and Spring Boot.

## Backend tests and coverage

Use Java 25 (set `JAVA_HOME` accordingly), Maven, and a running Docker daemon for
the Testcontainers integration tests. From the repository root, run:

```bash
mvn clean verify
```

For one service and its reactor dependencies, for example:

```bash
mvn -pl post-service -am clean verify
```

JaCoCo writes each tested service's report to
`<module>/target/site/jacoco/index.html` and the full reactor report to
`target/site/jacoco-aggregate/index.html`. Open an `index.html` file in a
browser. The aggregate is produced only when the `coverage-report` module is
built, as in the full command; a service-only build does not produce it.
Because the root participates in the service-only command, `clean` also
removes any previous aggregate report.

The aggregate includes `post-service`, `leaderboard-service`, `user-service`,
`api-gateway`, and the shared `leaderboard-contract`. The contract has no tests
of its own, so it has no individual execution data or report; coverage of its
classes exercised by service tests appears in the aggregate. The reporting
module contains no application code, and the React frontend uses separate
coverage tooling.

## Services

- API Gateway — external application entry point
- User Service
- Post Service
- Leaderboard Service

## Infrastructure

- PostgreSQL
- Apache Kafka
- Redis

## Docker Compose

- API Gateway: `http://localhost:8081`
- Kafka UI: `http://localhost:18081`
- Backend application services are not published to host ports.
- Gateway downstream communication uses Docker service names.

Start the stack:

```bash
docker compose up --build
```

## CORS

No frontend origin is configured by default because no deployment-confirmed origin is present in the project. Set confirmed origins explicitly before browser access:

```bash
GATEWAY_CORS_ALLOWED_ORIGINS=https://frontend.example.com docker compose up --build
```

For multiple origins, use a comma-separated list. Wildcard origins are not enabled.

## Architecture

Clients → API Gateway → Post/User/Leaderboard Services

Post Service → PostgreSQL
Post Service → Kafka → Leaderboard Service → Redis

## Profile activity API

`GET /api/posts/users/{userId}/activity` is available through the Gateway to authenticated users. `userId` must be a positive integer; invalid IDs return HTTP 400. Post Service does not own user accounts, so an unknown positive ID returns zero counts; profile existence is determined by User Service.

The response contains six non-negative integer counts:

```json
{
  "postsCreated": 12,
  "commentsAuthored": 35,
  "postLikesGiven": 20,
  "commentLikesGiven": 14,
  "postLikesReceived": 48,
  "commentLikesReceived": 9
}
```

Counts reflect currently stored posts, comments, and their separate like targets, including self-likes. Removing likes or deleting their target content changes these counts; they are not leaderboard scores or historical totals.