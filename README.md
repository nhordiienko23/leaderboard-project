# Leaderboard Project

Event-driven microservices project built with Java and Spring Boot.

## Services

- Post Service
- Leaderboard Service

## Infrastructure

- PostgreSQL
- Apache Kafka
- Redis

## Architecture

Post Service → PostgreSQL
Post Service → Kafka → Leaderboard Service → Redis