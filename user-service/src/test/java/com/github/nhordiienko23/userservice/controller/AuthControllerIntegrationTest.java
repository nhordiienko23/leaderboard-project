package com.github.nhordiienko23.userservice.controller;

import com.github.nhordiienko23.leaderboardcontract.event.UserEventType;
import com.github.nhordiienko23.userservice.entity.RefreshToken;
import com.github.nhordiienko23.userservice.entity.User;
import com.github.nhordiienko23.userservice.entity.UserRole;
import com.github.nhordiienko23.userservice.repository.RefreshTokenRepository;
import com.github.nhordiienko23.userservice.repository.UserEventOutboxRepository;
import com.github.nhordiienko23.userservice.repository.UserRepository;
import com.github.nhordiienko23.userservice.testcontainers.AbstractUserServiceIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@AutoConfigureMockMvc(addFilters = false)
@Sql(
        statements = AuthControllerIntegrationTest.RESET_DATABASE,
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD
)
@Sql(
        statements = AuthControllerIntegrationTest.RESET_DATABASE,
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD
)
@DisplayName("Auth controller integration tests")
class AuthControllerIntegrationTest extends AbstractUserServiceIntegrationTest {

    static final String RESET_DATABASE =
            "TRUNCATE TABLE user_event_outbox, refresh_tokens, users RESTART IDENTITY";

    private static final String BASE = "/api/auth";
    private static final String PASSWORD = "FixturePassword1!";
    private static final String ISSUER = "http://user-service";

    private static final long ACCESS_TOKEN_TTL_SECONDS = 900L;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private RefreshTokenRepository refreshTokens;

    @Autowired
    private UserEventOutboxRepository outbox;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtEncoder jwtEncoder;

    @BeforeEach
    @DisplayName("Configure JWT encoder mock")
    void configureJwtEncoder() {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusSeconds(ACCESS_TOKEN_TTL_SECONDS);

        Jwt accessToken = Jwt.withTokenValue("test-access-token")
                .header("alg", "RS256")
                .claim("iss", ISSUER)
                .claim("sub", "test-user")
                .claim("aud", List.of("api-gateway"))
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build();

        when(jwtEncoder.encode(any(JwtEncoderParameters.class)))
                .thenReturn(accessToken);
    }

    @Nested
    @DisplayName("POST /api/auth/register")
    class Register {

        @Test
        @DisplayName("Should create a user and enqueue an event when the request is valid")
        void registerShouldPersistUserAndEnqueueEventWhenRequestIsValid()
                throws Exception {
            MockHttpServletResponse response = perform(
                    post(BASE + "/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(registerBody(
                                    "new-user",
                                    "new-user@example.com",
                                    PASSWORD
                            )),
                    201
            );

            User saved = users.findByEmailIgnoreCase("new-user@example.com")
                    .orElseThrow();

            assertThat(jsonBody(response))
                    .containsEntry("username", "new-user")
                    .containsEntry("email", "new-user@example.com")
                    .containsEntry("role", "USER")
                    .doesNotContainKey("password");

            assertThat(saved.getUsername()).isEqualTo("new-user");
            assertThat(saved.getRole()).isEqualTo(UserRole.USER);
            assertThat(passwordEncoder.matches(PASSWORD, saved.getPassword()))
                    .isTrue();

            assertThat(outbox.findAll())
                    .singleElement()
                    .satisfies(event -> {
                        assertThat(event.getUserId()).isEqualTo(saved.getId());
                        assertThat(event.getUsername()).isEqualTo("new-user");
                        assertThat(event.getEventType())
                                .isEqualTo(UserEventType.CREATED);
                        assertThat(event.isPublished()).isFalse();
                    });
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "{\"username\":\" \",\"email\":\"new@example.com\",\"password\":\"ValidPassword1!\"}",
                "{\"username\":\"new-user\",\"email\":\"not-an-email\",\"password\":\"ValidPassword1!\"}",
                "{\"username\":\"new-user\",\"email\":\"new@example.com\",\"password\":\" \"}"
        })
        @DisplayName("Should reject invalid requests without saving a user")
        void registerShouldRejectRequestWhenRequestIsInvalid(String body)
                throws Exception {
            perform(
                    post(BASE + "/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body),
                    400
            );

            assertThat(users.count()).isZero();
            assertThat(outbox.count()).isZero();
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "{\"username\":\"existing-user\",\"email\":\"new@example.com\",\"password\":\"ValidPassword1!\"}",
                "{\"username\":\"new-user\",\"email\":\"EXISTING@example.com\",\"password\":\"ValidPassword1!\"}"
        })
        @DisplayName("Should reject duplicate usernames or emails without saving a user")
        void registerShouldReturnConflictWhenUsernameOrEmailAlreadyExists(String body)
                throws Exception {
            saveUser("existing-user", "existing@example.com");

            perform(
                    post(BASE + "/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body),
                    409
            );

            assertThat(users.count()).isEqualTo(1);
            assertThat(outbox.count()).isZero();
        }
    }

    @Nested
    @DisplayName("POST /api/auth/login")
    class Login {

        @ParameterizedTest
        @ValueSource(strings = {"existing-user", "existing@example.com"})
        @DisplayName("Should issue tokens when username or email and password are valid")
        void loginShouldIssueTokensWhenCredentialsAreValid(String identifier)
                throws Exception {
            saveUser("existing-user", "existing@example.com");

            MockHttpServletResponse response = perform(
                    post(BASE + "/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginBody(identifier, PASSWORD)),
                    200
            );

            assertTokenResponse(jsonBody(response));

            assertThat(refreshTokens.findAll())
                    .singleElement()
                    .satisfies(token -> {
                        assertThat(token.getUserId()).isNotNull();
                        assertThat(token.getTokenHash()).hasSize(64);
                    });
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "{\"login\":\"unknown-user\",\"password\":\"FixturePassword1!\"}",
                "{\"login\":\"existing-user\",\"password\":\"IncorrectPassword1!\"}"
        })
        @DisplayName("Should reject invalid credentials without issuing a refresh token")
        void loginShouldReturnUnauthorizedWhenCredentialsAreInvalid(String body)
                throws Exception {
            saveUser("existing-user", "existing@example.com");

            perform(
                    post(BASE + "/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body),
                    401
            );

            assertThat(refreshTokens.count()).isZero();
        }
    }

    @Nested
    @DisplayName("POST /api/auth/refresh")
    class Refresh {

        @Test
        @DisplayName("Should rotate the token and reject reuse of the old token")
        void refreshShouldRotateTokenAndRejectReplayWhenTokenIsValid()
                throws Exception {
            saveUser("existing-user", "existing@example.com");

            String oldToken = loginAndGetRefreshToken("existing-user");

            RefreshToken original = refreshTokens.findAll().getFirst();

            MockHttpServletResponse response = perform(
                    post(BASE + "/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(refreshBody(oldToken)),
                    200
            );

            assertTokenResponse(jsonBody(response));

            assertThat(refreshTokens.findAll()).hasSize(2);

            assertThat(refreshTokens.findById(original.getId())
                    .orElseThrow()
                    .getRevokedAt()).isNotNull();

            assertThat(refreshTokens.findAll())
                    .filteredOn(token -> token.getRevokedAt() == null)
                    .hasSize(1);

            perform(
                    post(BASE + "/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(refreshBody(oldToken)),
                    401
            );

            assertThat(refreshTokens.findAll()).hasSize(2);
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "{\"refreshToken\":\"unknown-token\"}",
                "{\"refreshToken\":\" \"}",
                "{}"
        })
        @DisplayName("Should reject invalid requests without creating tokens")
        void refreshShouldRejectRequestWhenTokenIsInvalid(String body)
                throws Exception {
            int expectedStatus = body.equals("{\"refreshToken\":\"unknown-token\"}")
                    ? 401
                    : 400;

            perform(
                    post(BASE + "/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body),
                    expectedStatus
            );

            assertThat(refreshTokens.count()).isZero();
        }
    }

    @Nested
    @DisplayName("POST /api/auth/logout")
    class Logout {

        @Test
        @DisplayName("Should revoke the refresh token when it is valid")
        void logoutShouldRevokeTokenWhenTokenIsValid() throws Exception {
            saveUser("existing-user", "existing@example.com");

            String token = loginAndGetRefreshToken("existing-user");

            RefreshToken issued = refreshTokens.findAll().getFirst();

            perform(
                    post(BASE + "/logout")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(refreshBody(token)),
                    204
            );

            assertThat(refreshTokens.findById(issued.getId())
                    .orElseThrow()
                    .getRevokedAt()).isNotNull();

            perform(
                    post(BASE + "/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(refreshBody(token)),
                    401
            );
        }
    }

    private User saveUser(String username, String email) {
        return users.saveAndFlush(User.builder()
                .username(username)
                .email(email)
                .password(passwordEncoder.encode(PASSWORD))
                .role(UserRole.USER)
                .build());
    }

    private String loginAndGetRefreshToken(String identifier) throws Exception {
        MockHttpServletResponse response = perform(
                post(BASE + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(identifier, PASSWORD)),
                200
        );

        return JsonPath.parse(response.getContentAsString())
                .read("$.refreshToken", String.class);
    }

    private void assertTokenResponse(Map<String, Object> body) {
        assertThat(body)
                .containsKey("accessToken")
                .containsKey("refreshToken")
                .containsEntry("tokenType", "Bearer");

        Number expiresIn = (Number) body.get("expiresIn");

        assertThat(expiresIn).isNotNull();
        assertThat(expiresIn.longValue()).isEqualTo(ACCESS_TOKEN_TTL_SECONDS);
    }

    private MockHttpServletResponse perform(
            MockHttpServletRequestBuilder request,
            int expectedStatus
    ) throws Exception {
        MockHttpServletResponse response = mvc.perform(request)
                .andReturn()
                .getResponse();

        assertThat(response.getStatus()).isEqualTo(expectedStatus);

        return response;
    }

    private static Map<String, Object> jsonBody(
            MockHttpServletResponse response
    ) throws Exception {
        return JsonPath.parse(response.getContentAsString()).read("$");
    }

    private static String registerBody(
            String username,
            String email,
            String password
    ) {
        return """
                {"username":"%s","email":"%s","password":"%s"}
                """.formatted(username, email, password);
    }

    private static String loginBody(String identifier, String password) {
        return """
                {"login":"%s","password":"%s"}
                """.formatted(identifier, password);
    }

    private static String refreshBody(String token) {
        return """
                {"refreshToken":"%s"}
                """.formatted(token);
    }
}
