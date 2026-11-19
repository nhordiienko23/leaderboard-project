# API Gateway Development Instructions

## Scope
- Build an independent Spring Boot application inside the monorepo.
- Expose the gateway on port `8081`.
- Implement routing, JWT authentication, public/protected request classification, and trusted identity propagation.
- Do not implement business functionality belonging to backend services.
- Follow the implementation plan and implement only the requested phase.

## Architecture and Build
- Confirm the root Maven aggregator and existing module configuration before adding modules.
- Include `user-service` and `api-gateway` consistently with the existing Maven build.
- Do not upgrade existing services unless dependency compatibility or actual build results justify it.
- Verify Spring Boot `4.1.1` and Spring Cloud `2025.1.4` compatibility using:
    - Official release documentation.
    - Maven dependency resolution.
    - An actual Maven build.
- Document unresolved version or compatibility uncertainty; do not invent dependencies or configuration.

## Java and Framework
- Use Java 25 and the project's existing Spring conventions.
- Use Spring Cloud Gateway Server Web MVC APIs only.
- Implement custom Gateway filters with `HandlerFilterFunction`, `ServerRequest`, and `ServerResponse`.
- Do not use WebFlux, `GlobalFilter`, `Mono`, `Flux`, or reactive security APIs.
- Use constructor injection and configuration properties for environment-specific values.
- Preserve existing package, configuration, and managed dependency conventions.
- Add dependencies only when required and confirmed by the source code or official documentation.

## Routing
- Route only verified, existing application endpoints.
- Inspect controller mappings before documenting or implementing routes.
- Route `/api/auth/**`, `/api/users/**`, and `/api/admin/users/**` to `user-service`.
- Route `/api/posts/**` to `post-service`.
- Route `/api/leaderboard/**` to `leaderboard-service`.
- Keep routing and authorization rules separate.
- Do not invent route names, HTTP methods, claims, configuration, or downstream services.
- Verify Docker Compose port assignments:
    - Gateway host port: `8081`.
    - Kafka UI host port: a different available port.

## Security Rules
- Protect all endpoints by default.
- Public endpoints must be limited to verified:
    - `POST` registration.
    - `POST` login.
    - `POST` refresh.
    - `POST` logout.
    - Explicitly verified public read endpoints for posts and leaderboard.
- Do not make `/api/auth/**`, `/api/posts/**`, `/api/leaderboard/**`, or `/api/users/**` groups public wholesale.
- Match public and protected rules by exact HTTP method and path.
- Allow required CORS `OPTIONS` preflight requests explicitly.
- Configure explicit allowed origins, methods, and headers.
- Do not allow all origins by default.
- Do not let an `OPTIONS` request bypass authentication for other HTTP methods.

## Authentication Implementation
- Use Spring Security Servlet Resource Server for JWT authentication.
- Do not implement JWT signature verification manually.
- Use the existing public RSA PEM key to validate JWTs.
- Do not assume an OIDC discovery or JWKS endpoint at `http://user-service`.
- Verify:
    - RS256 signature.
    - Issuer.
    - Audience.
    - Expiration (`exp`).
    - Not-before (`nbf`).
    - Numeric subject (`sub`).
    - Role claim.
- Use Spring Security JWT validators and the authenticated JWT principal.
- Validate JWT before forwarding protected requests.
- Use Gateway MVC `HandlerFilterFunction` only for request header sanitization and trusted identity propagation.
- Obtain identity exclusively from the authenticated `JwtAuthenticationToken`.
- Never derive identity from an unverified JWT, raw `Authorization` header, or client-provided identity headers.

## Identity Propagation
- Remove client-provided `X-User-Id` and `X-User-Role` from every request before forwarding.
- Add identity headers only after successful JWT authentication.
- Populate headers only from verified JWT claims:
    - `X-User-Id` from the numeric `sub` claim.
    - `X-User-Role` from the verified role claim.
- Do not inject identity headers into public requests, including public requests containing spoofed identity headers.
- Preserve backend business authorization; Gateway authentication does not replace microservice authorization.
- Keep the private RSA key exclusively in `user-service`.
- Do not log authorization headers, tokens, private keys, passwords, or raw sensitive claims.

## Network Security
- In production, expose only the API Gateway to external clients.
- Keep backend services accessible only through the private application network.
- Do not publish backend service ports publicly in production.
- Never rely exclusively on `X-User-Id` or `X-User-Role` for security when backend services are directly reachable by untrusted clients.

## Testing
- Follow JUnit 5, Mockito, AssertJ, and existing Spring test conventions.
- Every test class and test method requires `@DisplayName`.
- Name tests using `method_shouldResult_whenCondition`.
- Group related behavior with `@Nested`.
- Use `@ParameterizedTest` for repeated validation behavior.
- Use AssertJ assertions.
- Test one behavior per test.
- Use `@SpringBootTest` integration tests with MockMvc and downstream HTTP endpoints where appropriate.
- Explicitly test:
    - Identity-header spoofing.
    - Public requests with spoofed identity headers.
    - Missing, malformed, expired, wrong-issuer, and wrong-audience tokens.
    - Invalid subject and role claims.
    - Role propagation.
    - Public and protected endpoint classification.
    - CORS preflight.
    - Downstream routing and forwarded headers.
    - Authentication failures without downstream forwarding or side effects.
- Verify mock interactions where applicable.

## Implementation Discipline
- Keep the Gateway focused on routing and authentication.
- Do not introduce business logic, database access, Kafka, Redis, service discovery, or OpenFeign without an explicit requirement.
- Prefer simple, maintainable production-quality code.
- Follow SOLID, KISS, DRY, and YAGNI.
- Avoid unnecessary abstractions and duplicated configuration.
- Detailed implementation phases, file locations, acceptance criteria, and Maven/test commands belong in `api-gateway-implementation-plan.md`.