# API Gateway Implementation Plan

## Overview

Implement an independent Spring Boot API Gateway inside the existing monorepo.

The API Gateway is the single entry point for external client requests. It is responsible for:

- Routing requests to the correct downstream microservice.
- Authenticating requests using JWT access tokens.
- Authorizing requests based on the verified JWT role.
- Removing client-provided identity headers.
- Propagating trusted user identity through HTTP headers when required by downstream controllers.
- Configuring CORS.
- Integrating the Gateway and backend services into Docker Compose.

### Architecture and Security Principles

1. **API Gateway is responsible for authentication and authorization.** It validates incoming JWTs and decides whether a request can be forwarded.
2. **Downstream services do not validate JWTs and do not need Spring Security.** They receive trusted identity headers from the Gateway and execute their existing business logic.
3. Never trust client-provided `X-User-Id` or `X-User-Role` headers.
4. Always remove incoming identity headers before forwarding a request.
5. Obtain user ID and role exclusively from a successfully validated JWT.
6. Add only the identity headers required by the target downstream controller.
7. Do not change existing downstream controller contracts or introduce JWT authentication into downstream services.
8. Protect all endpoints by default. Public access must be explicitly defined by HTTP method and path.
9. Keep the implementation simple and consistent with the existing project architecture.

---

# Implementation Steps

## Phase 1. Baseline Maven Structure and Compatibility

### Goal

Verify the existing project structure and integrate the missing Maven modules.

### Tasks

- Inspect the root `pom.xml` and existing Maven module structure.
- Add `user-service` and `api-gateway` to the root Maven aggregator.
- Preserve the existing Maven structure and dependency management conventions.
- Confirm the existing Java 25, Spring Boot 4.1.1, and Spring Cloud 2025.1.4 versions.
- Verify compatibility using official Spring documentation, Maven dependency resolution, and an actual build.
- Verify support for:
    - Spring Cloud Gateway Server Web MVC.
    - Spring Security Servlet.
    - OAuth2 Resource Server.
    - JWT decoding and JOSE support.
- Do not upgrade existing modules or dependencies without a demonstrated compatibility requirement.

### Commands

Run:

```bash
mvn -N dependency:tree
mvn -pl api-gateway dependency:tree
mvn clean verify
```

### Acceptance Criteria

- `user-service` and `api-gateway` are included in the root Maven build.
- Dependency resolution succeeds.
- Java 25 and Spring Boot/Spring Cloud compatibility is verified.
- Existing modules continue to build successfully.

---

## Phase 2. Gateway Dependencies and Application Configuration

### Goal

Prepare the Gateway for routing, Servlet-based JWT authentication, and downstream communication.

### Tasks

- Inspect the existing `api-gateway` project structure and application entry point.
- Add only the dependencies required for:
    - Spring Cloud Gateway Server Web MVC.
    - Spring Security Servlet.
    - OAuth2 Resource Server.
    - OAuth2 JOSE/JWT decoding.
    - Configuration properties.
    - Unit and integration testing.
- Use dependency versions managed by the existing compatible BOMs wherever possible.
- Configure `application.yaml` with:
    - Server port `8081`.
    - JWT issuer: `http://user-service`.
    - JWT audience: `api-gateway`.
    - Configurable public-key location.
    - Configurable downstream service URLs.
    - Explicit CORS properties.
- Use constructor injection and `@ConfigurationProperties` for application configuration.
- Keep the application stateless.
- Inspect the existing refresh-token transport before making CSRF decisions. Do not introduce cookie-based authentication or change existing authentication semantics.

### Downstream Service URLs

Use configurable URLs with the existing service ports:

| Service | Local URL |
|---|---|
| User Service | `http://localhost:8084` |
| Post Service | `http://localhost:8080` |
| Leaderboard Service | `http://localhost:8082` |

These URLs must be configurable so Docker Compose can use internal Docker service names.

### Acceptance Criteria

- Gateway starts on port `8081`.
- Required dependencies resolve successfully.
- The public RSA key can be loaded through configurable application properties.
- No WebFlux, database, Kafka, Redis, service discovery, or unrelated infrastructure is introduced into the Gateway.

---

## Phase 3. Implement Verified Request Routing

### Goal

Route requests to the correct existing microservice without changing downstream endpoint contracts.

### Tasks

Inspect existing controllers and implement Gateway routes for the confirmed mappings.

| Downstream Service | Route |
|---|---|
| User Service | `/api/auth/**` |
| User Service | `/api/users/**` |
| User Service | `/api/admin/users/**` |
| Post Service | `/api/posts/**` |
| Leaderboard Service | `/api/leaderboard/**` |

Requirements:

- Preserve the original HTTP method.
- Preserve the original URI path.
- Preserve query parameters and request bodies.
- Do not invent endpoint mappings.
- Do not introduce unnecessary path rewriting or prefixes.
- Ensure unknown paths are not implicitly forwarded to any backend.
- Register the required Gateway MVC filters on every forwarding route.

### Acceptance Criteria

- All confirmed routes reach their corresponding downstream applications.
- Original paths, HTTP methods, query parameters, and request bodies are preserved.
- Unknown paths are not forwarded.
- Every forwarding route has the required identity-header handling.

---

## Phase 4. Implement JWT Authentication and Gateway Authorization

### Goal

Make API Gateway responsible for validating JWT access tokens and authorizing requests before forwarding them.

### 4.1. JWT Authentication

Use Spring Security Servlet OAuth2 Resource Server inside API Gateway.

Do not implement manual JWT signature verification.

Configure an RS256 `JwtDecoder` using the existing public RSA key:

`secrets/user-service-public.pem`

The private key must remain exclusively inside User Service.

Validate:

- Signature using RS256.
- Issuer: `http://user-service`.
- Audience contains `api-gateway`.
- Expiration (`exp`).
- Not-before (`nbf`).
- Subject (`sub`) is a positive numeric user ID.
- Role claim is present and contains only `USER` or `ADMIN`.

Use Spring Security JWT validators and appropriate custom validators for application-specific claims.

After successful validation, use the authenticated `JwtAuthenticationToken` as the trusted source of JWT claims.

Never derive identity from a raw Authorization header or unvalidated JWT payload.

### 4.2. Public Endpoints

The following exact HTTP method and path combinations must be accessible without an access JWT:

**User Service authentication endpoints**

| Method | Path |
|---|---|
| POST | `/api/auth/register` |
| POST | `/api/auth/login` |
| POST | `/api/auth/refresh` |
| POST | `/api/auth/logout` |

These endpoints remain responsible for their existing downstream credential and refresh-token validation.

**Post Service public read endpoints**

| Method | Path |
|---|---|
| GET | `/api/posts` |
| GET | `/api/posts/{id}` |
| GET | `/api/posts/{postId}/comments` |
| GET | `/api/posts/{postId}/comments/{commentId}` |

**Leaderboard Service public read endpoints**

| Method | Path |
|---|---|
| GET | `/api/leaderboard/top-10` |
| GET | `/api/leaderboard/top-100` |

Use exact method/path matchers. Do not make entire controller path groups public.

Verify these mappings against the actual controllers before implementation.

### 4.3. Protected Endpoints

Protect every endpoint that is not explicitly listed as public.

- A valid JWT is required for protected requests.
- `/api/admin/users/**` requires the verified JWT role to be `ADMIN`.
- Other protected endpoints require a valid authenticated JWT.
- Requests with an invalid or missing JWT must be rejected before reaching downstream services.
- Requests from `USER` to admin endpoints must receive `403 Forbidden`.
- Do not forward unauthorized requests.

### 4.4. Role Authorization

Read the `role` claim directly from the successfully validated `JwtAuthenticationToken`.

Use a simple Gateway authorization mechanism to check whether the verified role is `ADMIN` for `/api/admin/users/**`.

A `JwtAuthenticationConverter` is not required if authorization is implemented directly against the verified JWT claim. Do not introduce unnecessary role-conversion infrastructure.

### Acceptance Criteria

- Exact public HTTP method/path combinations work without an access JWT.
- Every other endpoint requires a valid JWT.
- Only `ADMIN` can access `/api/admin/users/**`.
- Invalid JWTs and unauthorized roles are rejected before downstream forwarding.
- No Spring Security or JWT validation is added to downstream services.

---

## Phase 5. Implement Trusted Identity Header Propagation

### Goal

Remove untrusted identity headers and add verified user information only when required by the target downstream controller.

### 5.1. Remove Client-Provided Identity Headers

For every incoming request that can be forwarded to a downstream service:

- Remove `X-User-Id`.
- Remove `X-User-Role`.

This applies to both public and protected requests.

Never preserve identity headers supplied by the client.

### 5.2. Obtain Trusted Identity

For an authenticated protected request:

- Obtain the verified `JwtAuthenticationToken` from the Spring Security authentication context.
- Read the user ID from the validated numeric `sub` claim.
- Read the user role from the validated `role` claim.
- Never use values from client-provided headers.

### 5.3. Add Only Required Headers

Inspect all existing downstream controller methods and their `@RequestHeader` declarations.

Create an endpoint-to-header mapping based on the actual controller contracts.

Known examples:

| Downstream Endpoint | Required Headers |
|---|---|
| `POST /api/admin/users` | `X-User-Id` |
| `PATCH /api/admin/users/{id}` | `X-User-Id` |
| `DELETE /api/admin/users/{id}` | `X-User-Id` |
| `PATCH /api/admin/users/{id}/password` | `X-User-Id` |
| `PATCH /api/admin/users/{id}/role` | `X-User-Id` |
| `POST /api/posts/{postId}/comments` | `X-User-Id` |
| `PUT /api/posts/{postId}/comments/{commentId}` | `X-User-Id`, `X-User-Role` |
| `DELETE /api/posts/{postId}/comments/{commentId}` | `X-User-Id`, `X-User-Role` |
| `GET /api/posts/{postId}/comments` | No identity headers |
| `GET /api/posts/{postId}/comments/{commentId}` | No identity headers |

Important:

- This table contains confirmed examples, not necessarily the complete mapping.
- Inspect all User Service, Post Service, and Leaderboard Service controllers before finalizing the mapping.
- Add headers only when the target controller requires them.
- Do not add `X-User-Role` to Admin User Service requests when only `X-User-Id` is required.
- Do not add identity headers to public requests.
- Do not propagate identity headers merely because a request belongs to a particular service.

### 5.4. Gateway MVC Filter

Implement trusted identity propagation using Spring Cloud Gateway Server Web MVC `HandlerFilterFunction` or an equivalent supported Gateway MVC route filter.

Requirements:

- Register the filter for every forwarding route.
- Remove untrusted identity headers before forwarding.
- For protected endpoints, add only the headers required by the target controller using verified JWT claims.
- For public endpoints, remove identity headers and do not add new ones.
- Preserve all unrelated request headers.

Do not use WebFlux `GlobalFilter`, `Mono`, `Flux`, or reactive security APIs.

### Acceptance Criteria

- Client-provided identity headers are never forwarded unchanged.
- Protected requests contain only Gateway-generated identity headers required by the downstream endpoint.
- Public requests contain no identity headers.
- User ID and role always come from the validated JWT.
- Existing downstream controllers work without modifications.
- No request is forwarded after authentication or authorization failure.

---

## Phase 6. CORS and Docker Compose Integration

### Goal

Configure secure browser access and expose only the API Gateway externally.

### 6.1. CORS

Configure explicit CORS settings through `application.yaml`.

Requirements:

- Use only deployment-confirmed frontend origins.
- Do not invent frontend origins.
- Do not use wildcard origins.
- Configure allowed methods and headers explicitly.
- Process valid CORS preflight requests before Spring Security authentication.
- Do not permit every OPTIONS request as a blanket authentication bypass.
- Actual protected requests must still require a valid JWT.
- Preserve the identity-header trust rules.

Test preflight requests to protected endpoints without JWTs and verify that actual requests still require authentication.

### 6.2. Docker Compose

Modify the existing `docker-compose.yml` to integrate the Gateway with the existing services.

Requirements:

- Expose API Gateway on host port `8081`.
- Keep Post Service on internal port `8080`.
- Keep Leaderboard Service on internal port `8082`.
- Keep User Service on internal port `8084`.
- Do not publish backend service ports on the host.
- Configure Gateway downstream URLs using Docker Compose service names.
- Ensure Gateway and backend services can communicate over an internal Docker network.
- Expose only the Gateway as the application entry point.
- Preserve existing Kafka and other infrastructure configuration unless a change is necessary.

### 6.3. Kafka UI Port

The current Kafka UI host port conflicts with Gateway port `8081`.

- Move Kafka UI to a different explicitly selected host port, such as `18081`.
- Check that the selected port is available.
- Update the Compose configuration and relevant documentation.

### 6.4. Public RSA Key in Docker

- Mount `secrets/user-service-public.pem` into the Gateway container as a read-only file.
- Make its container path configurable.
- Do not copy or mount `secrets/user-service-private.pem` into the Gateway container.
- Keep the private RSA key exclusively under User Service.

### Acceptance Criteria

- Gateway is accessible externally through port `8081`.
- Backend services have no published host ports.
- Gateway reaches backend services through Docker service names.
- Kafka UI no longer conflicts with the Gateway.
- Gateway can load the public key in Docker.
- The private RSA key is never available inside the Gateway container.

---

## Phase 7. Testing and Final Verification

### Goal

Verify routing, authentication, authorization, trusted header propagation, CORS, and Docker integration.

### Unit Tests

Use:

- JUnit 5.
- Mockito.
- AssertJ.
- `@DisplayName` on every test class and test method.
- Test naming convention: `method_shouldResult_whenCondition`.
- `@Nested` for logically grouped tests.
- Parameterized tests for similar validation cases.

Focus on:

- JWT claim validation.
- Public/protected endpoint classification.
- Admin role authorization.
- Identity header sanitization.
- Endpoint-specific identity header propagation.

### Integration Tests

Use `@SpringBootTest` with MockMvc or the appropriate supported Servlet MVC testing setup.

Use WireMock or MockWebServer as mock downstream HTTP services.

Verify:

**JWT authentication**

- Missing JWT.
- Malformed JWT.
- Expired JWT.
- Wrong issuer.
- Wrong audience.
- Invalid subject.
- Invalid role.
- Incorrect signature.

**Authorization**

- Anonymous access to exact public endpoints.
- Authenticated access to protected endpoints.
- `USER` denied from admin endpoints.
- `ADMIN` allowed to access admin endpoints.
- No downstream request after authentication or authorization failure.

**Identity propagation**

- Client-provided `X-User-Id` is replaced or removed.
- Client-provided `X-User-Role` is replaced or removed.
- Correct verified user ID is propagated.
- Correct verified role is propagated only where required.
- Admin requests propagate only `X-User-Id` when that is all the controller requires.
- Public requests do not receive identity headers.
- Endpoints that do not declare identity headers do not receive unnecessary identity headers.

**Routing**

- Correct downstream service.
- Correct HTTP method.
- Original URI path preserved.
- Query parameters preserved.
- Request body preserved.
- Required headers forwarded.
- No forwarding for unknown routes.

**CORS**

- Valid preflight requests.
- Disallowed origins.
- Disallowed methods.
- Actual protected requests still require authentication.

**Docker**

- Gateway starts successfully in Docker.
- Public key is accessible.
- Backend services are reachable through internal service names.
- Backend host ports are not published.

### Final Verification

Run:

```bash
mvn clean verify
```

Also verify the complete Docker Compose startup and application-to-application communication.

### Acceptance Criteria

- All tests pass.
- All existing modules continue to build.
- All protected requests are authenticated and authorized by the Gateway.
- Downstream services receive trusted identity headers according to their existing controller contracts.
- No downstream service requires Spring Security or JWT validation.
- All application traffic enters through the Gateway.

---

# Implementation Rules

- Implement one phase at a time.
- Do not start the next phase until the current phase builds and its tests pass.
- Follow the existing package structure and coding conventions.
- Use constructor injection.
- Follow SOLID, KISS, DRY, and YAGNI.
- Prefer simple, maintainable implementations.
- Do not introduce unnecessary abstractions.
- Do not modify downstream controllers, services, or DTOs to accommodate the Gateway.
- Do not introduce WebFlux or reactive APIs.
- Do not add a database, Kafka, Redis, service discovery, or unrelated infrastructure to the Gateway.
- Do not expose backend services directly to external clients.
- Do not log JWTs, authorization headers, passwords, private keys, or other sensitive information.
- Do not invent endpoints, frontend origins, or configuration values.
- When the existing codebase does not provide enough information, inspect the relevant files and document the uncertainty instead of making assumptions.

The implementation plan is a guide for incremental development. The actual implementation must always be based on the existing codebase and verified controller contracts.