package com.github.nhordiienko23.apigateway;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.env.Environment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("API Gateway Application")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiGatewayApplicationTests {

    private static HttpServer postServer;
    private static HttpServer leaderboardServer;
    private static HttpServer userServer;
    private static final List<CapturedRequest> POST_REQUESTS = new ArrayList<>();
    private static final List<CapturedRequest> LEADERBOARD_REQUESTS = new ArrayList<>();
    private static final List<CapturedRequest> USER_REQUESTS = new ArrayList<>();

    @Autowired
    private Environment environment;

    @LocalServerPort
    private int gatewayPort;

    @BeforeAll
    static void startMockDownstreamServices() throws IOException {
        postServer = startServer(POST_REQUESTS);
        leaderboardServer = startServer(LEADERBOARD_REQUESTS);
        userServer = startServer(USER_REQUESTS);
    }

    @AfterAll
    static void stopMockDownstreamServices() {
        postServer.stop(0);
        leaderboardServer.stop(0);
        userServer.stop(0);
    }

    @DynamicPropertySource
    static void gatewayProperties(DynamicPropertyRegistry registry) {
        registry.add("gateway.downstream-services.post-service",
                () -> "http://localhost:" + postServer.getAddress().getPort());
        registry.add("gateway.downstream-services.leaderboard-service",
                () -> "http://localhost:" + leaderboardServer.getAddress().getPort());
        registry.add("gateway.downstream-services.user-service",
                () -> "http://localhost:" + userServer.getAddress().getPort());
        registry.add("gateway.jwt.public-key-location", TestJwtSupport::publicKeyLocation);
        registry.add("gateway.cors.allowed-origins", () -> "https://cors-test.example,https://cors-test-two.example");
    }

    @Test
    @DisplayName("contextLoads_shouldStart_whenApplicationConfigurationIsValid")
    void contextLoads_shouldStart_whenApplicationConfigurationIsValid() {
    }

    @Test
    @DisplayName("routes_shouldContainConfirmedMappings_whenConfigurationIsLoaded")
    void routes_shouldContainConfirmedMappings_whenConfigurationIsLoaded() {
        assertRoute("0", "post-service", "/api/posts/**",
                "http://localhost:" + postServer.getAddress().getPort());
        assertRoute("1", "leaderboard-service", "/api/leaderboard/**",
                "http://localhost:" + leaderboardServer.getAddress().getPort());
        assertRoute("2", "user-auth-service", "/api/auth/**",
                "http://localhost:" + userServer.getAddress().getPort());
        assertRoute("3", "user-service", "/api/users/**",
                "http://localhost:" + userServer.getAddress().getPort());
        assertRoute("4", "user-admin-service", "/api/admin/users/**",
                "http://localhost:" + userServer.getAddress().getPort());
    }

    @Test
    @DisplayName("routes_shouldForwardRequestsToTheCorrectServices_whenTokensAndPublicRulesAreValid")
    void routes_shouldForwardRequestsToTheCorrectServices_whenTokensAndPublicRulesAreValid() {
        clearRequests();
        String userToken = bearer(TestJwtSupport.accessToken("USER"));
        String adminToken = bearer(TestJwtSupport.accessToken("ADMIN"));

        RestClient gateway = RestClient.create("http://localhost:" + gatewayPort);

        gateway.post().uri("/api/posts/42/comments?draft=false")
                .header("Authorization", userToken)
                .header("Content-Type", "application/json")
                .body("{\"text\":\"hello\"}")
                .retrieve().toBodilessEntity();

        gateway.get().uri("/api/leaderboard/top-10?page=2")
                .retrieve().toBodilessEntity();

        gateway.post().uri("/api/auth/login?attempt=1")
                .header("Content-Type", "application/json")
                .body("{\"username\":\"test\"}")
                .retrieve().toBodilessEntity();

        gateway.get().uri("/api/users/7?projection=minimal")
                .header("Authorization", userToken)
                .retrieve().toBodilessEntity();

        gateway.delete().uri("/api/admin/users/7?reason=test")
                .header("Authorization", adminToken)
                .retrieve().toBodilessEntity();

        assertThat(POST_REQUESTS).singleElement().satisfies(request -> {
            assertThat(request.method()).isEqualTo("POST");
            assertThat(request.requestTarget()).isEqualTo("/api/posts/42/comments?draft=false");
            assertThat(request.body()).isEqualTo("{\"text\":\"hello\"}");
        });
        assertThat(LEADERBOARD_REQUESTS).singleElement().satisfies(request -> {
            assertThat(request.method()).isEqualTo("GET");
            assertThat(request.requestTarget()).isEqualTo("/api/leaderboard/top-10?page=2");
        });
        assertThat(USER_REQUESTS).hasSize(3);
        assertThat(USER_REQUESTS.get(0).method()).isEqualTo("POST");
        assertThat(USER_REQUESTS.get(0).requestTarget()).isEqualTo("/api/auth/login?attempt=1");
        assertThat(USER_REQUESTS.get(0).body()).isEqualTo("{\"username\":\"test\"}");
        assertThat(USER_REQUESTS.get(1).requestTarget()).isEqualTo("/api/users/7?projection=minimal");
        assertThat(USER_REQUESTS.get(2).method()).isEqualTo("DELETE");
        assertThat(USER_REQUESTS.get(2).requestTarget()).isEqualTo("/api/admin/users/7?reason=test");
    }

    @Test
    void publicUserSummaryShouldForwardWithoutAuthentication() {
        clearRequests();
        assertThat(exchangeStatus("GET", "/api/users/7/summary", null)).isEqualTo(200);
        assertThat(USER_REQUESTS).singleElement().matches(request ->
                request.requestTarget().equals("/api/users/7/summary"));
    }

    @Test
    @DisplayName("protectedEndpoint_shouldReturnUnauthorized_whenTokenIsMissing")
    void protectedEndpoint_shouldReturnUnauthorized_whenTokenIsMissing() {
        clearRequests();
        int status = exchangeStatus("GET", "/api/users/7", null);
        assertThat(status).isEqualTo(401);
        assertThat(USER_REQUESTS).isEmpty();
    }

    @Test
    @DisplayName("protectedEndpoint_shouldReturnUnauthorized_whenTokenIsInvalid")
    void protectedEndpoint_shouldReturnUnauthorized_whenTokenIsInvalid() {
        clearRequests();
        int status = exchangeStatus("GET", "/api/users/7", bearer("not-a-jwt"));
        assertThat(status).isEqualTo(401);
        assertThat(USER_REQUESTS).isEmpty();
    }

    @Test
    @DisplayName("adminEndpoint_shouldReturnForbidden_whenRoleIsUser")
    void adminEndpoint_shouldReturnForbidden_whenRoleIsUser() {
        clearRequests();
        int status = exchangeStatus("DELETE", "/api/admin/users/7", bearer(TestJwtSupport.accessToken("USER")));
        assertThat(status).isEqualTo(403);
        assertThat(USER_REQUESTS).isEmpty();
    }

    @Test
    @DisplayName("adminEndpoint_shouldForward_whenRoleIsAdmin")
    void adminEndpoint_shouldForward_whenRoleIsAdmin() {
        clearRequests();
        int status = exchangeStatus("DELETE", "/api/admin/users/7", bearer(TestJwtSupport.accessToken("ADMIN")));
        assertThat(status).isEqualTo(200);
        assertThat(USER_REQUESTS).singleElement().matches(request ->
                request.requestTarget().equals("/api/admin/users/7")
        );
    }

    @Test
    @DisplayName("publicEndpoint_shouldForward_whenTokenIsMissing")
    void publicEndpoint_shouldForward_whenTokenIsMissing() {
        clearRequests();
        int status = exchangeStatus("GET", "/api/posts", null);
        assertThat(status).isEqualTo(200);
        assertThat(POST_REQUESTS).singleElement();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/posts/42/engagement",
            "/api/posts/42/comments/7/engagement"
    })
    @DisplayName("engagementSummary_shouldForwardWithoutIdentity_whenJwtIsMissing")
    void engagementSummary_shouldForwardWithoutIdentity_whenJwtIsMissing(String path) {
        clearRequests();
        int status = RestClient.create("http://localhost:" + gatewayPort).get().uri(path)
                .header("X-User-Id", "999")
                .header("X-User-Role", "ADMIN")
                .exchange((request, response) -> response.getStatusCode().value());

        assertThat(status).isEqualTo(200);
        assertThat(POST_REQUESTS).singleElement().satisfies(request -> {
            assertThat(request.requestTarget()).isEqualTo(path);
            assertThat(headerValue(request, "X-User-Id")).isNull();
            assertThat(headerValue(request, "X-User-Role")).isNull();
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/posts/42/engagement",
            "/api/posts/42/comments/7/engagement"
    })
    @DisplayName("engagementSummary_shouldForwardJwtIdentity_whenJwtIsValid")
    void engagementSummary_shouldForwardJwtIdentity_whenJwtIsValid(String path) {
        clearRequests();
        int status = RestClient.create("http://localhost:" + gatewayPort).get().uri(path)
                .header("Authorization", bearer(TestJwtSupport.accessToken("USER")))
                .header("X-User-Id", "999")
                .header("X-User-Role", "MODERATOR")
                .exchange((request, response) -> response.getStatusCode().value());

        assertThat(status).isEqualTo(200);
        assertThat(POST_REQUESTS).singleElement().satisfies(request -> {
            assertThat(headerValue(request, "X-User-Id")).isEqualTo("42");
            assertThat(headerValue(request, "X-User-Role")).isNull();
        });
    }

    @Test
    @DisplayName("engagementSummary_shouldRejectInvalidJwt_withoutForwardingSpoofedIdentity")
    void engagementSummary_shouldRejectInvalidJwt_withoutForwardingSpoofedIdentity() {
        clearRequests();
        int status = RestClient.create("http://localhost:" + gatewayPort).get()
                .uri("/api/posts/42/engagement")
                .header("Authorization", bearer("not-a-jwt"))
                .header("X-User-Id", "999")
                .exchange((request, response) -> response.getStatusCode().value());

        assertThat(status).isEqualTo(401);
        assertThat(POST_REQUESTS).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/posts/42/engagement/extra",
            "/api/posts/42/comments/7/engagement/extra"
    })
    @DisplayName("engagementException_shouldNotApplyToUnintendedGetPaths")
    void engagementException_shouldNotApplyToUnintendedGetPaths(String path) {
        clearRequests();

        assertThat(exchangeStatus("GET", path, null)).isEqualTo(401);
        assertThat(POST_REQUESTS).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/posts/42/engagement",
            "/api/posts/42/comments/7/engagement"
    })
    @DisplayName("engagementException_shouldNotApplyToOtherMethods")
    void engagementException_shouldNotApplyToOtherMethods(String path) {
        clearRequests();
        int status = RestClient.create("http://localhost:" + gatewayPort).post().uri(path)
                .exchange((request, response) -> response.getStatusCode().value());

        assertThat(status).isEqualTo(401);
        assertThat(POST_REQUESTS).isEmpty();
    }

    @Test
    @DisplayName("existingPublicPostEndpoint_shouldStillStripIdentity_whenJwtIsValid")
    void existingPublicPostEndpoint_shouldStillStripIdentity_whenJwtIsValid() {
        clearRequests();
        int status = RestClient.create("http://localhost:" + gatewayPort).get().uri("/api/posts")
                .header("Authorization", bearer(TestJwtSupport.accessToken("USER")))
                .header("X-User-Id", "999")
                .header("X-User-Role", "ADMIN")
                .exchange((request, response) -> response.getStatusCode().value());

        assertThat(status).isEqualTo(200);
        assertThat(POST_REQUESTS).singleElement().satisfies(request -> {
            assertThat(headerValue(request, "X-User-Id")).isNull();
            assertThat(headerValue(request, "X-User-Role")).isNull();
        });
    }

    @Test
    @DisplayName("invalidIssuer_shouldReturnUnauthorized_whenTokenIsRejected")
    void invalidIssuer_shouldReturnUnauthorized_whenTokenIsRejected() {
        assertInvalidToken(TestJwtSupport.accessToken("wrong-issuer", TestJwtSupport.AUDIENCE,
                "42", "USER", Instant.now().minusSeconds(5), Instant.now().plusSeconds(300),
                Instant.now().minusSeconds(60)));
    }

    @Test
    @DisplayName("invalidAudience_shouldReturnUnauthorized_whenTokenIsRejected")
    void invalidAudience_shouldReturnUnauthorized_whenTokenIsRejected() {
        assertInvalidToken(TestJwtSupport.accessToken(TestJwtSupport.ISSUER, "wrong-audience",
                "42", "USER", Instant.now().minusSeconds(5), Instant.now().plusSeconds(300),
                Instant.now().minusSeconds(60)));
    }

    @Test
    @DisplayName("expiredToken_shouldReturnUnauthorized_whenTokenIsRejected")
    void expiredToken_shouldReturnUnauthorized_whenTokenIsRejected() {
        assertInvalidToken(TestJwtSupport.accessToken(TestJwtSupport.ISSUER, TestJwtSupport.AUDIENCE,
                "42", "USER", Instant.now().minusSeconds(300), Instant.now().minusSeconds(60),
                Instant.now().minusSeconds(60)));
    }

    @Test
    @DisplayName("futureNbf_shouldReturnUnauthorized_whenTokenIsRejected")
    void futureNbf_shouldReturnUnauthorized_whenTokenIsRejected() {
        assertInvalidToken(TestJwtSupport.accessToken(TestJwtSupport.ISSUER, TestJwtSupport.AUDIENCE,
                "42", "USER", Instant.now().minusSeconds(5), Instant.now().plusSeconds(300),
                Instant.now().plusSeconds(300)));
    }

    @Test
    @DisplayName("invalidSubject_shouldReturnUnauthorized_whenTokenIsRejected")
    void invalidSubject_shouldReturnUnauthorized_whenTokenIsRejected() {
        assertInvalidToken(TestJwtSupport.accessToken(TestJwtSupport.ISSUER, TestJwtSupport.AUDIENCE,
                "0", "USER", Instant.now().minusSeconds(5), Instant.now().plusSeconds(300),
                Instant.now().minusSeconds(60)));
    }

    @Test
    @DisplayName("invalidRole_shouldReturnUnauthorized_whenTokenIsRejected")
    void invalidRole_shouldReturnUnauthorized_whenTokenIsRejected() {
        assertInvalidToken(TestJwtSupport.accessToken(TestJwtSupport.ISSUER, TestJwtSupport.AUDIENCE,
                "42", "MODERATOR", Instant.now().minusSeconds(5), Instant.now().plusSeconds(300),
                Instant.now().minusSeconds(60)));
    }

    @Test
    @DisplayName("invalidSignature_shouldReturnUnauthorized_whenTokenIsRejected")
    void invalidSignature_shouldReturnUnauthorized_whenTokenIsRejected() {
        assertInvalidToken(TestJwtSupport.accessTokenWithInvalidSignature());
    }

    @Test
    @DisplayName("tokenWithoutExpiration_shouldReturnUnauthorized_whenTokenIsRejected")
    void tokenWithoutExpiration_shouldReturnUnauthorized_whenTokenIsRejected() {
        assertInvalidToken(TestJwtSupport.accessTokenWithoutExpiration());
    }

    @Test
    @DisplayName("unknownPath_shouldReturnNotFound_whenValidTokenUsesAnUnmatchedRoute")
    void unknownPath_shouldReturnNotFound_whenValidTokenUsesAnUnmatchedRoute() {
        clearRequests();
        int status = exchangeStatus("GET", "/api/unknown/path", bearer(TestJwtSupport.accessToken("USER")));

        assertThat(status).isEqualTo(404);
        assertThat(POST_REQUESTS).isEmpty();
        assertThat(LEADERBOARD_REQUESTS).isEmpty();
        assertThat(USER_REQUESTS).isEmpty();
    }

    @Test
    @DisplayName("identityHeaders_shouldBeReplaced_whenProtectedUserEndpointIsForwarded")
    void identityHeaders_shouldBeReplaced_whenProtectedUserEndpointIsForwarded() {
        clearRequests();
        int status = exchangeStatusWithClientIdentityHeaders("GET", "/api/users/7", bearer(TestJwtSupport.accessToken("USER")));
        assertThat(status).isEqualTo(200);
        assertThat(USER_REQUESTS).singleElement().satisfies(request -> {
            assertThat(headerValue(request, "X-User-Id")).isEqualTo("42");
            assertThat(headerValue(request, "X-User-Role")).isNull();
        });
    }

    @Test
    @DisplayName("identityHeaders_shouldContainVerifiedClaims_whenPostRoleEndpointIsForwarded")
    void identityHeaders_shouldContainVerifiedClaims_whenPostRoleEndpointIsForwarded() {
        clearRequests();
        RestClient gateway = RestClient.create("http://localhost:" + gatewayPort);
        int status = gateway.put().uri("/api/posts/42")
                .header("Authorization", bearer(TestJwtSupport.accessToken("ADMIN")))
                .header("X-User-Id", "999")
                .header("X-User-Role", "MODERATOR")
                .header("Content-Type", "application/json")
                .body("{\"title\":\"updated\"}")
                .exchange((request, response) -> response.getStatusCode().value());
        assertThat(status).isEqualTo(200);
        assertThat(POST_REQUESTS).singleElement().satisfies(request -> {
            assertThat(headerValue(request, "X-User-Id")).isEqualTo("42");
            assertThat(headerValue(request, "X-User-Role")).isEqualTo("ADMIN");
        });
    }

    @Test
    @DisplayName("identityHeaders_shouldBeRemoved_whenPublicEndpointIsForwarded")
    void identityHeaders_shouldBeRemoved_whenPublicEndpointIsForwarded() {
        clearRequests();
        RestClient gateway = RestClient.create("http://localhost:" + gatewayPort);
        int status = gateway.get().uri("/api/posts")
                .header("X-User-Id", "999")
                .header("X-User-Role", "ADMIN")
                .exchange((request, response) -> response.getStatusCode().value());
        assertThat(status).isEqualTo(200);
        assertThat(POST_REQUESTS).singleElement().satisfies(request -> {
            assertThat(headerValue(request, "X-User-Id")).isNull();
            assertThat(headerValue(request, "X-User-Role")).isNull();
        });
    }

    @Test
    @DisplayName("identityHeaders_shouldExcludeRole_whenAdminUserEndpointIsForwarded")
    void identityHeaders_shouldExcludeRole_whenAdminUserEndpointIsForwarded() {
        clearRequests();
        int status = exchangeStatusWithClientIdentityHeaders(
                "DELETE", "/api/admin/users/7", bearer(TestJwtSupport.accessToken("ADMIN")));
        assertThat(status).isEqualTo(200);
        assertThat(USER_REQUESTS).singleElement().satisfies(request -> {
            assertThat(headerValue(request, "X-User-Id")).isEqualTo("42");
            assertThat(headerValue(request, "X-User-Role")).isNull();
        });
    }

    @Test
    @DisplayName("allowedOrigin_shouldReceiveAllowOrigin_whenActualRequestIsSent")
    void allowedOrigin_shouldReceiveAllowOrigin_whenActualRequestIsSent() {
        RestClient gateway = RestClient.create("http://localhost:" + gatewayPort);
        CorsResponse response = gateway.get().uri("/api/posts")
                .header("Origin", "https://cors-test-two.example")
                .exchange((request, httpResponse) -> new CorsResponse(
                        httpResponse.getStatusCode().value(),
                        httpResponse.getHeaders().getFirst("Access-Control-Allow-Origin")));

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.allowOrigin()).isEqualTo("https://cors-test-two.example");
    }

    @Test
    @DisplayName("protectedPreflight_shouldNotReturnUnauthorized_whenOriginAndRequestMethodAreAllowed")
    void protectedPreflight_shouldNotReturnUnauthorized_whenOriginAndRequestMethodAreAllowed() {
        CorsResponse response = corsPreflight("GET", "https://cors-test.example");

        assertThat(response.status()).isNotEqualTo(401);
        assertThat(response.status()).isIn(200, 204);
    }

    @Test
    @DisplayName("protectedPreflight_shouldReceiveAllowedMethodsAndHeaders_whenRequestIsValid")
    void protectedPreflight_shouldReceiveAllowedMethodsAndHeaders_whenRequestIsValid() {
        RestClient gateway = RestClient.create("http://localhost:" + gatewayPort);
        CorsResponse response = gateway.options().uri("/api/users/7")
                .header("Origin", "https://cors-test.example")
                .header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "Authorization,Content-Type")
                .exchange((request, httpResponse) -> new CorsResponse(
                        httpResponse.getStatusCode().value(),
                        httpResponse.getHeaders().getFirst("Access-Control-Allow-Origin"),
                        httpResponse.getHeaders().getFirst("Access-Control-Allow-Methods"),
                        httpResponse.getHeaders().getFirst("Access-Control-Allow-Headers")));

        assertThat(response.status()).isIn(200, 204);
        assertThat(response.allowOrigin()).isEqualTo("https://cors-test.example");
        assertThat(response.allowMethods()).contains("GET");
        assertThat(response.allowHeaders()).containsIgnoringCase("Authorization");
        assertThat(response.allowHeaders()).containsIgnoringCase("Content-Type");
    }

    @Test
    @DisplayName("unconfiguredOrigin_shouldNotReceiveAllowOrigin_whenActualRequestIsSent")
    void unconfiguredOrigin_shouldNotReceiveAllowOrigin_whenActualRequestIsSent() {
        RestClient gateway = RestClient.create("http://localhost:" + gatewayPort);
        CorsResponse response = gateway.get().uri("/api/posts")
                .header("Origin", "https://unconfigured.example")
                .exchange((request, httpResponse) -> new CorsResponse(
                        httpResponse.getStatusCode().value(),
                        httpResponse.getHeaders().getFirst("Access-Control-Allow-Origin")));

        assertThat(response.allowOrigin()).isNull();
    }

    @Test
    @DisplayName("protectedActualRequest_shouldReturnUnauthorized_whenCORSOriginIsAllowedButJwtIsMissing")
    void protectedActualRequest_shouldReturnUnauthorized_whenCORSOriginIsAllowedButJwtIsMissing() {
        clearRequests();
        RestClient gateway = RestClient.create("http://localhost:" + gatewayPort);
        CorsResponse response = gateway.get().uri("/api/users/7")
                .header("Origin", "https://cors-test.example")
                .exchange((request, httpResponse) -> new CorsResponse(
                        httpResponse.getStatusCode().value(),
                        httpResponse.getHeaders().getFirst("Access-Control-Allow-Origin")));

        assertThat(response.status()).isEqualTo(401);
        assertThat(response.allowOrigin()).isEqualTo("https://cors-test.example");
        assertThat(USER_REQUESTS).isEmpty();
    }

    @Test
    @DisplayName("disallowedOrigin_shouldNotReceiveAllowOrigin_whenPreflightIsSent")
    void disallowedOrigin_shouldNotReceiveAllowOrigin_whenPreflightIsSent() {
        RestClient gateway = RestClient.create("http://localhost:" + gatewayPort);
        CorsResponse response = gateway.options().uri("/api/users/7")
                .header("Origin", "https://unconfigured.example")
                .header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "Authorization")
                .exchange((request, httpResponse) -> new CorsResponse(
                        httpResponse.getStatusCode().value(),
                        httpResponse.getHeaders().getFirst("Access-Control-Allow-Origin")));

        assertThat(response.allowOrigin()).isNull();
    }

    @Test
    @DisplayName("disallowedMethod_shouldNotReceiveAllowOrigin_whenPreflightIsSent")
    void disallowedMethod_shouldNotReceiveAllowOrigin_whenPreflightIsSent() {
        RestClient gateway = RestClient.create("http://localhost:" + gatewayPort);
        CorsResponse response = gateway.options().uri("/api/users/7")
                .header("Origin", "https://cors-test.example")
                .header("Access-Control-Request-Method", "TRACE")
                .header("Access-Control-Request-Headers", "Authorization")
                .exchange((request, httpResponse) -> new CorsResponse(
                        httpResponse.getStatusCode().value(),
                        httpResponse.getHeaders().getFirst("Access-Control-Allow-Origin")));

        assertThat(response.allowOrigin()).isNull();
    }


    private CorsResponse corsPreflight(String method, String origin) {
        RestClient gateway = RestClient.create("http://localhost:" + gatewayPort);
        return gateway.options().uri("/api/users/7")
                .header("Origin", origin)
                .header("Access-Control-Request-Method", method)
                .header("Access-Control-Request-Headers", "Authorization,Content-Type")
                .exchange((request, httpResponse) -> new CorsResponse(
                        httpResponse.getStatusCode().value(),
                        httpResponse.getHeaders().getFirst("Access-Control-Allow-Origin")));
    }

    private void assertInvalidToken(String token) {
        clearRequests();
        int status = exchangeStatus("GET", "/api/users/7", bearer(token));
        assertThat(status).isEqualTo(401);
        assertThat(USER_REQUESTS).isEmpty();
    }

    private int exchangeStatus(String method, String uri, String authorization) {
        RestClient client = RestClient.create("http://localhost:" + gatewayPort);
        RestClient.RequestHeadersSpec<?> requestSpec;
        if ("GET".equals(method)) {
            requestSpec = client.get().uri(uri);
        } else if ("DELETE".equals(method)) {
            requestSpec = client.delete().uri(uri);
        } else {
            throw new IllegalArgumentException("Unsupported test method: " + method);
        }
        if (authorization != null) {
            requestSpec.header("Authorization", authorization);
        }
        return requestSpec.exchange((request, response) -> response.getStatusCode().value());
    }

    private int exchangeStatusWithClientIdentityHeaders(String method, String uri, String authorization) {
        RestClient client = RestClient.create("http://localhost:" + gatewayPort);
        RestClient.RequestHeadersSpec<?> requestSpec;
        if ("GET".equals(method)) {
            requestSpec = client.get().uri(uri);
        } else if ("DELETE".equals(method)) {
            requestSpec = client.delete().uri(uri);
        } else {
            throw new IllegalArgumentException("Unsupported test method: " + method);
        }
        requestSpec.header("Authorization", authorization)
                .header("X-User-Id", "999")
                .header("X-User-Role", "MODERATOR");
        return requestSpec.exchange((request, response) -> response.getStatusCode().value());
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private void clearRequests() {
        POST_REQUESTS.clear();
        LEADERBOARD_REQUESTS.clear();
        USER_REQUESTS.clear();
    }

    private void assertRoute(String index, String id, String path, String uri) {
        String base = "spring.cloud.gateway.server.webmvc.routes[" + index + "]";
        assertThat(environment.getProperty(base + ".id")).isEqualTo(id);
        assertThat(environment.getProperty(base + ".uri")).isEqualTo(uri);
        assertThat(environment.getProperty(base + ".predicates[0]")).isEqualTo("Path=" + path);
    }

    private static HttpServer startServer(List<CapturedRequest> requests) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> capture(exchange, requests));
        server.start();
        return server;
    }

    private static void capture(HttpExchange exchange, List<CapturedRequest> requests) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        requests.add(new CapturedRequest(exchange.getRequestMethod(), exchange.getRequestURI().toString(), body, headersToMap(exchange)));
        byte[] response = "{}".getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }

    private static Map<String, List<String>> headersToMap(HttpExchange exchange) {
        return exchange.getRequestHeaders().entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> new ArrayList<>(entry.getValue()),
                        (existing, replacement) -> existing,
                        () -> new TreeMap<>(String.CASE_INSENSITIVE_ORDER)));
    }

    private static String headerValue(CapturedRequest request, String name) {
        List<String> values = request.headers().get(name);
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    private record CapturedRequest(String method, String requestTarget, String body,
                                   Map<String, List<String>> headers) {
    }

    private record CorsResponse(int status, String allowOrigin, String allowMethods, String allowHeaders) {
        CorsResponse(int status, String allowOrigin) {
            this(status, allowOrigin, null, null);
        }
    }
}
