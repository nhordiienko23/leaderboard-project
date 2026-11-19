# Leaderboard Project

Event-driven microservices project built with Java and Spring Boot.

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