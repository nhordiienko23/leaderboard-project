package com.github.nhordiienko23.apigateway.filter;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Trusted Identity Header Filter")
class TrustedIdentityHeaderFilterTest {

    private TrustedIdentityHeaderFilter filter;

    @BeforeEach
    void setUp() {
        filter = new TrustedIdentityHeaderFilter();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("Public Request Header Sanitization")
    class PublicRequestHeaderSanitizationTests {

        @ParameterizedTest
        @CsvSource({
                "GET,/api/users/42/summary",
                "GET,/api/posts",
                "GET,/api/posts/42",
                "GET,/api/posts/42/comments",
                "GET,/api/posts/42/comments/7",
                "GET,/api/leaderboard/top-10",
                "GET,/api/leaderboard/top-100"
        })
        @DisplayName("identityHeaders_shouldBeRemoved_whenPublicGetRequestIsForwarded")
        void identityHeaders_shouldBeRemoved_whenPublicGetRequestIsForwarded(
                String method, String path
        ) throws Exception {
            authenticateJwt("ADMIN");

            MockHttpServletRequest request = request(method, path);
            request.addHeader("X-User-Id", "999");
            request.addHeader("X-User-Role", "ADMIN");

            CapturingFilterChain chain = new CapturingFilterChain();
            filter.doFilter(request, new MockHttpServletResponse(), chain);

            HttpServletRequest forwarded = chain.request;
            assertThat(forwarded.getHeader("X-User-Id")).isNull();
            assertThat(forwarded.getHeader("X-User-Role")).isNull();
        }

        @ParameterizedTest
        @CsvSource({
                "POST,/api/auth/register",
                "POST,/api/auth/login",
                "POST,/api/auth/refresh",
                "POST,/api/auth/logout"
        })
        @DisplayName("identityHeaders_shouldBeRemoved_whenPublicAuthRequestIsForwarded")
        void identityHeaders_shouldBeRemoved_whenPublicAuthRequestIsForwarded(
                String method, String path
        ) throws Exception {
            authenticateJwt("ADMIN");

            MockHttpServletRequest request = request(method, path);
            request.addHeader("X-User-Id", "999");
            request.addHeader("X-User-Role", "ADMIN");

            CapturingFilterChain chain = new CapturingFilterChain();
            filter.doFilter(request, new MockHttpServletResponse(), chain);

            assertThat(chain.request.getHeader("X-User-Id")).isNull();
            assertThat(chain.request.getHeader("X-User-Role")).isNull();
        }
    }

    @Nested
    @DisplayName("Endpoint Identity Header Mapping")
    class EndpointIdentityHeaderMappingTests {

        @ParameterizedTest
        @CsvSource({
                "GET,/api/users/7",
                "GET,/api/users",
                "PATCH,/api/users/me",
                "DELETE,/api/users/me",
                "PATCH,/api/users/me/password",
                "POST,/api/admin/users",
                "PATCH,/api/admin/users/7",
                "DELETE,/api/admin/users/7",
                "PATCH,/api/admin/users/7/password",
                "PATCH,/api/admin/users/7/role",
                "POST,/api/posts",
                "POST,/api/posts/42/comments",
                "POST,/api/posts/42/likes",
                "DELETE,/api/posts/42/likes",
                "POST,/api/posts/42/comments/7/likes",
                "DELETE,/api/posts/42/comments/7/likes"
        })
        @DisplayName("identityHeaders_shouldContainVerifiedIdAndNoRole_whenEndpointRequiresOnlyId")
        void identityHeaders_shouldContainVerifiedIdAndNoRole_whenEndpointRequiresOnlyId(
                String method, String path
        ) throws Exception {
            authenticateJwt("ADMIN");

            MockHttpServletRequest request = request(method, path);
            request.addHeader("X-User-Id", "999");
            request.addHeader("X-User-Role", "MODERATOR");

            CapturingFilterChain chain = new CapturingFilterChain();
            filter.doFilter(request, new MockHttpServletResponse(), chain);

            assertThat(chain.request.getHeader("X-User-Id")).isEqualTo("42");
            assertThat(chain.request.getHeader("X-User-Role")).isNull();
        }

        @ParameterizedTest
        @CsvSource({
                "PUT,/api/posts/42,USER",
                "DELETE,/api/posts/42,ADMIN",
                "PUT,/api/posts/42/comments/7,USER",
                "DELETE,/api/posts/42/comments/7,ADMIN"
        })
        @DisplayName("identityHeaders_shouldContainVerifiedIdAndRole_whenEndpointRequiresBoth")
        void identityHeaders_shouldContainVerifiedIdAndRole_whenEndpointRequiresBoth(
                String method, String path, String role
        ) throws Exception {
            authenticateJwt(role);

            MockHttpServletRequest request = request(method, path);
            request.addHeader("X-User-Id", "999");
            request.addHeader("X-User-Role", "MODERATOR");

            CapturingFilterChain chain = new CapturingFilterChain();
            filter.doFilter(request, new MockHttpServletResponse(), chain);

            assertThat(chain.request.getHeader("X-User-Id")).isEqualTo("42");
            assertThat(chain.request.getHeader("X-User-Role")).isEqualTo(role);
        }

        @Test
        @DisplayName("identityHeaders_shouldBeRemoved_whenProtectedRequestHasNoAuthentication")
        void identityHeaders_shouldBeRemoved_whenProtectedRequestHasNoAuthentication()
                throws Exception {
            MockHttpServletRequest request = request("POST", "/api/posts");
            request.addHeader("X-User-Id", "999");
            request.addHeader("X-User-Role", "ADMIN");

            CapturingFilterChain chain = new CapturingFilterChain();
            filter.doFilter(request, new MockHttpServletResponse(), chain);

            assertThat(chain.request.getHeader("X-User-Id")).isNull();
            assertThat(chain.request.getHeader("X-User-Role")).isNull();
        }

        @Test
        @DisplayName("identityHeaders_shouldBeRemoved_whenProtectedRequestHasNonJwtAuthentication")
        void identityHeaders_shouldBeRemoved_whenProtectedRequestHasNonJwtAuthentication()
                throws Exception {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken("user", "password")
            );

            MockHttpServletRequest request = request("POST", "/api/posts");
            request.addHeader("X-User-Id", "999");
            request.addHeader("X-User-Role", "ADMIN");

            CapturingFilterChain chain = new CapturingFilterChain();
            filter.doFilter(request, new MockHttpServletResponse(), chain);

            assertThat(chain.request.getHeader("X-User-Id")).isNull();
            assertThat(chain.request.getHeader("X-User-Role")).isNull();
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "/api/posts/42/engagement",
                "/api/posts/42/comments/7/engagement"
        })
        @DisplayName("engagementIdentity_shouldComeFromJwt_whenEngagementSummaryIsForwarded")
        void engagementIdentity_shouldComeFromJwt_whenEngagementSummaryIsForwarded(String path)
                throws Exception {
            authenticateJwt("USER");

            MockHttpServletRequest request = request("GET", path);
            request.addHeader("X-User-Id", "999");
            request.addHeader("X-User-Role", "ADMIN");

            CapturingFilterChain chain = new CapturingFilterChain();
            filter.doFilter(request, new MockHttpServletResponse(), chain);

            assertThat(chain.request.getHeader("X-User-Id")).isEqualTo("42");
            assertThat(chain.request.getHeader("X-User-Role")).isNull();
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "/api/posts/42/engagement",
                "/api/posts/42/comments/7/engagement"
        })
        @DisplayName("engagementIdentity_shouldBeRemoved_whenJwtIsMissing")
        void engagementIdentity_shouldBeRemoved_whenJwtIsMissing(String path)
                throws Exception {
            MockHttpServletRequest request = request("GET", path);
            request.addHeader("X-User-Id", "999");
            request.addHeader("X-User-Role", "ADMIN");

            CapturingFilterChain chain = new CapturingFilterChain();
            filter.doFilter(request, new MockHttpServletResponse(), chain);

            assertThat(chain.request.getHeader("X-User-Id")).isNull();
            assertThat(chain.request.getHeader("X-User-Role")).isNull();
        }
    }

    private static MockHttpServletRequest request(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRequestURI(path);
        return request;
    }

    private static void authenticateJwt(String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt(role))
        );
    }

    private static Jwt jwt(String role) {
        return Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .subject("42")
                .claim("role", role)
                .issuedAt(Instant.now().minusSeconds(5))
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
    }

    private static final class CapturingFilterChain implements jakarta.servlet.FilterChain {
        private HttpServletRequest request;

        @Override
        public void doFilter(jakarta.servlet.ServletRequest request,
                             jakarta.servlet.ServletResponse response) {
            this.request = (HttpServletRequest) request;
        }
    }
}
