package com.github.nhordiienko23.userservice.controller;

import com.github.nhordiienko23.leaderboardcontract.event.UserEventType;
import com.github.nhordiienko23.userservice.entity.User;
import com.github.nhordiienko23.userservice.entity.UserRole;
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
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@AutoConfigureMockMvc(addFilters = false)
@Sql(
        statements = AdminUserControllerIntegrationTest.RESET_DATABASE,
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD
)
@Sql(
        statements = AdminUserControllerIntegrationTest.RESET_DATABASE,
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD
)
@DisplayName("Admin user controller integration tests")
class AdminUserControllerIntegrationTest extends AbstractUserServiceIntegrationTest {

    static final String RESET_DATABASE =
            "TRUNCATE TABLE user_event_outbox, refresh_tokens, users RESTART IDENTITY";

    private static final String BASE = "/api/admin/users";
    private static final String ACTOR_HEADER = "X-User-Id";
    private static final String ORIGINAL_PASSWORD = "FixturePassword1!";
    private static final String NEW_PASSWORD = "ReplacementPassword2!";

    private static final String CREATE_BODY = """
            {
              "username": "new-user",
              "email": "new-user@example.com",
              "password": "NewUserPassword1!",
              "role": "USER"
            }
            """;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private UserEventOutboxRepository outbox;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User admin;
    private User target;

    @BeforeEach
    void seedUsers() {
        String passwordHash = passwordEncoder.encode(ORIGINAL_PASSWORD);

        admin = saveUser("test-admin", UserRole.ADMIN, passwordHash);
        target = saveUser("test-user", UserRole.USER, passwordHash);
    }

    @Nested
    @DisplayName("POST /api/admin/users")
    class CreateUser {

        @Test
        @DisplayName("Should create a user and enqueue an event when requested by an admin")
        void createUserShouldPersistUserAndEnqueueEventWhenRequestedByAdmin()
                throws Exception {
            MockHttpServletResponse response = perform(
                    post(BASE)
                            .header(ACTOR_HEADER, admin.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(CREATE_BODY),
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
            assertThat(passwordEncoder.matches(
                    "NewUserPassword1!",
                    saved.getPassword()
            )).isTrue();

            assertPendingEvent(saved.getId(), "new-user", UserEventType.CREATED);
        }

        @Test
        @DisplayName("Should return 401 when the actor header is missing")
        void createUserShouldReturnUnauthorizedWhenActorHeaderIsMissing()
                throws Exception {
            perform(
                    post(BASE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(CREATE_BODY),
                    401
            );

            assertNoUserCreated();
        }

        @Test
        @DisplayName("Should return 403 when the actor is not an admin")
        void createUserShouldReturnForbiddenWhenActorIsNotAdmin()
                throws Exception {
            perform(
                    post(BASE)
                            .header(ACTOR_HEADER, target.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(CREATE_BODY),
                    403
            );

            assertNoUserCreated();
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "{\"email\":\"new-user@example.com\",\"password\":\"TestPassword1!\",\"role\":\"USER\"}",
                "{\"username\":\" \",\"email\":\"new-user@example.com\",\"password\":\"TestPassword1!\",\"role\":\"USER\"}",
                "{\"username\":\"new-user\",\"email\":\"not-an-email\",\"password\":\"TestPassword1!\",\"role\":\"USER\"}"
        })
        @DisplayName("Should return 400 when the creation request is invalid")
        void createUserShouldReturnBadRequestWhenRequestIsInvalid(String body)
                throws Exception {
            perform(
                    post(BASE)
                            .header(ACTOR_HEADER, admin.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body),
                    400
            );

            assertNoUserCreated();
        }

        @Test
        @DisplayName("Should return 409 when the username already exists in another case")
        void createUserShouldReturnConflictWhenUsernameAlreadyExistsInAnotherCase()
                throws Exception {
            MockHttpServletResponse response = perform(
                    post(BASE)
                            .header(ACTOR_HEADER, admin.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "username": "TEST-USER",
                                      "email": "new-user@example.com",
                                      "password": "NewUserPassword1!",
                                      "role": "USER"
                                    }
                                    """),
                    409
            );

            assertThat(jsonBody(response))
                    .containsEntry("message", "Username already exists");

            assertNoUserCreated();
        }
    }

    @Nested
    @DisplayName("PATCH /api/admin/users/{id}")
    class UpdateUserProfile {

        @Test
        @DisplayName("Should update the username and enqueue an event when it changes")
        void updateUserProfileShouldPersistUsernameAndEnqueueEventWhenUsernameChanges()
                throws Exception {
            MockHttpServletResponse response = perform(
                    patch(BASE + "/{id}", target.getId())
                            .header(ACTOR_HEADER, admin.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"username":"renamed-user"}
                                    """),
                    200
            );

            assertThat(jsonBody(response))
                    .containsEntry("username", "renamed-user");

            User updated = reloadTarget();

            assertThat(updated.getUsername()).isEqualTo("renamed-user");
            assertThat(updated.getEmail()).isEqualTo("test-user@example.com");

            assertPendingEvent(
                    target.getId(),
                    "renamed-user",
                    UserEventType.UPDATED
            );
        }
    }

    @Nested
    @DisplayName("DELETE /api/admin/users/{id}")
    class DeleteUser {

        @Test
        @DisplayName("Should delete the target user when it exists")
        void deleteUserShouldRemoveTargetWhenItExists() throws Exception {
            perform(
                    delete(BASE + "/{id}", target.getId())
                            .header(ACTOR_HEADER, admin.getId()),
                    204
            );

            assertThat(users.findById(target.getId())).isEmpty();
            assertThat(users.findById(admin.getId())).isPresent();
        }

        @Test
        @DisplayName("Should return 404 when the target user does not exist")
        void deleteUserShouldReturnNotFoundWhenTargetDoesNotExist()
                throws Exception {
            MockHttpServletResponse response = perform(
                    delete(BASE + "/{id}", Long.MAX_VALUE)
                            .header(ACTOR_HEADER, admin.getId()),
                    404
            );

            assertThat(jsonBody(response))
                    .containsEntry("message", "User not found");

            assertThat(users.count()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("PATCH /api/admin/users/{id}/password")
    class SetUserPassword {

        @Test
        @DisplayName("Should replace the stored password hash when the new password is valid")
        void setUserPasswordShouldReplaceStoredHashWhenNewPasswordIsValid()
                throws Exception {
            perform(
                    patch(BASE + "/{id}/password", target.getId())
                            .header(ACTOR_HEADER, admin.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"newPassword":"%s"}
                                    """.formatted(NEW_PASSWORD)),
                    204
            );

            String updatedHash = reloadTarget().getPassword();

            assertThat(passwordEncoder.matches(NEW_PASSWORD, updatedHash)).isTrue();
            assertThat(passwordEncoder.matches(ORIGINAL_PASSWORD, updatedHash)).isFalse();
        }

        @Test
        @DisplayName("Should return 400 when the password exceeds 72 UTF-8 bytes")
        void setUserPasswordShouldReturnBadRequestWhenPasswordExceeds72Utf8Bytes()
                throws Exception {
            String password = "é".repeat(37);

            MockHttpServletResponse response = perform(
                    patch(BASE + "/{id}/password", target.getId())
                            .header(ACTOR_HEADER, admin.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"newPassword":"%s"}
                                    """.formatted(password)),
                    400
            );

            assertThat(jsonBody(response))
                    .containsEntry(
                            "message",
                            "Password must not exceed 72 UTF-8 bytes"
                    );

            assertThat(reloadTarget().getPassword())
                    .isEqualTo(target.getPassword());
        }
    }

    @Nested
    @DisplayName("PATCH /api/admin/users/{id}/role")
    class UpdateUserRole {

        @Test
        @DisplayName("Should update the user role when requested by an admin")
        void updateUserRoleShouldPersistNewRoleWhenRequestedByAdmin()
                throws Exception {
            MockHttpServletResponse response = perform(
                    patch(BASE + "/{id}/role", target.getId())
                            .header(ACTOR_HEADER, admin.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"role":"ADMIN"}
                                    """),
                    200
            );

            assertThat(jsonBody(response))
                    .containsEntry("role", "ADMIN");

            assertThat(reloadTarget().getRole()).isEqualTo(UserRole.ADMIN);
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "{}",
                "{\"role\":null}",
                "{\"role\":\"UNKNOWN\"}"
        })
        @DisplayName("Should return 400 and preserve the role when the request is invalid")
        void updateUserRoleShouldPreserveRoleWhenRequestIsInvalid(String body)
                throws Exception {
            perform(
                    patch(BASE + "/{id}/role", target.getId())
                            .header(ACTOR_HEADER, admin.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body),
                    400
            );

            assertThat(reloadTarget().getRole()).isEqualTo(UserRole.USER);
        }
    }

    private User saveUser(String username, UserRole role, String passwordHash) {
        return users.saveAndFlush(User.builder()
                .username(username)
                .email(username + "@example.com")
                .password(passwordHash)
                .role(role)
                .build());
    }

    private User reloadTarget() {
        return users.findById(target.getId()).orElseThrow();
    }

    private void assertNoUserCreated() {
        assertThat(users.count()).isEqualTo(2);
        assertThat(outbox.count()).isZero();
    }

    private void assertPendingEvent(
            Long userId,
            String username,
            UserEventType type
    ) {
        assertThat(outbox.findAll())
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getUserId()).isEqualTo(userId);
                    assertThat(event.getUsername()).isEqualTo(username);
                    assertThat(event.getEventType()).isEqualTo(type);
                    assertThat(event.isPublished()).isFalse();
                });
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
}
