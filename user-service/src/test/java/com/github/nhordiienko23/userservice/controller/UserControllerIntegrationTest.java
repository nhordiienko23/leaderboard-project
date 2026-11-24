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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

@AutoConfigureMockMvc(addFilters = false)
@Sql(
        statements = UserControllerIntegrationTest.RESET_DATABASE,
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD
)
@Sql(
        statements = UserControllerIntegrationTest.RESET_DATABASE,
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD
)
@DisplayName("User controller integration tests")
class UserControllerIntegrationTest extends AbstractUserServiceIntegrationTest {

    static final String RESET_DATABASE =
            "TRUNCATE TABLE user_event_outbox, refresh_tokens, users RESTART IDENTITY";

    private static final String BASE = "/api/users";
    private static final String ACTOR_HEADER = "X-User-Id";
    private static final String PASSWORD = "FixturePassword1!";
    private static final String NEW_PASSWORD = "ReplacementPassword2!";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private UserEventOutboxRepository outbox;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User actor;
    private User peer;

    @BeforeEach
    @DisplayName("Seed users for controller requests")
    void seedUsers() {
        String passwordHash = passwordEncoder.encode(PASSWORD);

        actor = saveUser("actor", "actor@example.com", passwordHash);
        peer = saveUser("peer", "peer@example.com", passwordHash);
    }

    @Test
    void getUserSummaryShouldExposeOnlyIdAndUsernameWithoutAuthentication() throws Exception {
        MockHttpServletResponse response = perform(
                get(BASE + "/{id}/summary", peer.getId()), 200
        );
        assertThat(jsonBody(response))
                .containsOnlyKeys("id", "username")
                .containsEntry("username", "peer");
    }

    @Test
    void getUserSummaryShouldReturnNotFoundForMissingUser() throws Exception {
        perform(get(BASE + "/{id}/summary", Long.MAX_VALUE), 404);
    }

    @Nested
    @DisplayName("GET /api/users/{id}")
    class GetUserById {

        @Test
        @DisplayName("Should return a public profile when authenticated")
        void getUserByIdShouldReturnPublicProfileWhenAuthenticated()
                throws Exception {
            MockHttpServletResponse response = perform(
                    get(BASE + "/{id}", peer.getId())
                            .header(ACTOR_HEADER, actor.getId()),
                    200
            );

            Map<String, Object> body = jsonBody(response);

            assertThat(((Number) body.get("id")).longValue())
                    .isEqualTo(peer.getId());
            assertThat(body)
                    .containsEntry("username", "peer")
                    .containsEntry("email", "peer@example.com")
                    .containsEntry("role", "USER")
                    .doesNotContainKey("password");
        }

        @Test
        @DisplayName("Should return 401 when the actor header is missing")
        void getUserByIdShouldReturnUnauthorizedWhenActorHeaderIsMissing()
                throws Exception {
            perform(get(BASE + "/{id}", peer.getId()), 401);
        }

        @Test
        @DisplayName("Should return 404 when the user does not exist")
        void getUserByIdShouldReturnNotFoundWhenUserDoesNotExist()
                throws Exception {
            MockHttpServletResponse response = perform(
                    get(BASE + "/{id}", Long.MAX_VALUE)
                            .header(ACTOR_HEADER, actor.getId()),
                    404
            );

            assertThat(jsonBody(response))
                    .containsEntry("message", "User not found");
        }
    }

    @Nested
    @DisplayName("GET /api/users")
    class GetUsers {

        @Test
        @DisplayName("Should return the requested page when authenticated")
        void getUsersShouldReturnRequestedPageWhenAuthenticated()
                throws Exception {
            MockHttpServletResponse response = perform(
                    get(BASE)
                            .header(ACTOR_HEADER, actor.getId())
                            .queryParam("page", "0")
                            .queryParam("size", "1")
                            .queryParam("sort", "id,asc"),
                    200
            );

            Map<String, Object> body = jsonBody(response);

            assertThat(body)
                    .containsEntry("page", 0)
                    .containsEntry("size", 1)
                    .containsEntry("totalElements", 2)
                    .containsEntry("totalPages", 2)
                    .containsEntry("first", true)
                    .containsEntry("last", false);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> content =
                    (List<Map<String, Object>>) body.get("content");

            assertThat(content)
                    .singleElement()
                    .satisfies(user ->
                            assertThat(((Number) user.get("id")).longValue())
                                    .isEqualTo(actor.getId())
                    );
        }

        @Test
        @DisplayName("Should return an empty page when the requested page is out of range")
        void getUsersShouldReturnEmptyPageWhenPageIsOutOfRange()
                throws Exception {
            MockHttpServletResponse response = perform(
                    get(BASE)
                            .header(ACTOR_HEADER, actor.getId())
                            .queryParam("page", "5")
                            .queryParam("size", "1"),
                    200
            );

            Map<String, Object> body = jsonBody(response);

            assertThat(body)
                    .containsEntry("content", List.of())
                    .containsEntry("totalElements", 2)
                    .containsEntry("totalPages", 2)
                    .containsEntry("last", true);
        }

        @Test
        @DisplayName("Should return 401 when the actor header is missing")
        void getUsersShouldReturnUnauthorizedWhenActorHeaderIsMissing()
                throws Exception {
            perform(get(BASE), 401);
        }
    }

    @Nested
    @DisplayName("PATCH /api/users/me")
    class UpdateMyProfile {

        @Test
        @DisplayName("Should update only the authenticated user and enqueue an event when the username changes")
        void updateMyProfileShouldUpdateAuthenticatedUserWhenUsernameChanges()
                throws Exception {
            MockHttpServletResponse response = perform(
                    patch(BASE + "/me")
                            .header(ACTOR_HEADER, actor.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"username":"renamed-actor","email":"renamed@example.com"}
                                    """),
                    200
            );

            Map<String, Object> body = jsonBody(response);

            assertThat(((Number) body.get("id")).longValue())
                    .isEqualTo(actor.getId());
            assertThat(body)
                    .containsEntry("username", "renamed-actor")
                    .containsEntry("email", "renamed@example.com");

            assertThat(users.findById(actor.getId()).orElseThrow().getUsername())
                    .isEqualTo("renamed-actor");

            assertThat(users.findById(peer.getId()).orElseThrow().getUsername())
                    .isEqualTo("peer");

            assertThat(outbox.findAll())
                    .singleElement()
                    .satisfies(event -> {
                        assertThat(event.getUserId()).isEqualTo(actor.getId());
                        assertThat(event.getUsername()).isEqualTo("renamed-actor");
                        assertThat(event.getEventType())
                                .isEqualTo(UserEventType.UPDATED);
                        assertThat(event.isPublished()).isFalse();
                    });
        }

        @Test
        @DisplayName("Should not enqueue an event when the profile values are unchanged")
        void updateMyProfileShouldNotEnqueueEventWhenValuesAreUnchanged()
                throws Exception {
            perform(
                    patch(BASE + "/me")
                            .header(ACTOR_HEADER, actor.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"username":"actor","email":"actor@example.com"}
                                    """),
                    200
            );

            User unchangedUser = users.findById(actor.getId()).orElseThrow();

            assertThat(unchangedUser.getUsername()).isEqualTo("actor");
            assertThat(unchangedUser.getEmail()).isEqualTo("actor@example.com");
            assertThat(outbox.count()).isZero();
        }

        @Test
        @DisplayName("Should reject a blank email and preserve the profile")
        void updateMyProfileShouldReturnBadRequestWhenEmailIsBlank()
                throws Exception {
            perform(
                    patch(BASE + "/me")
                            .header(ACTOR_HEADER, actor.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"username":"actor","email":" "}
                                    """),
                    400
            );

            User unchangedUser = users.findById(actor.getId()).orElseThrow();

            assertThat(unchangedUser.getUsername()).isEqualTo("actor");
            assertThat(unchangedUser.getEmail()).isEqualTo("actor@example.com");
            assertThat(outbox.count()).isZero();
        }

        @Test
        @DisplayName("Should return 409 and preserve data when the username is taken")
        void updateMyProfileShouldReturnConflictWhenUsernameIsTaken()
                throws Exception {
            MockHttpServletResponse response = perform(
                    patch(BASE + "/me")
                            .header(ACTOR_HEADER, actor.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"username":"peer"}
                                    """),
                    409
            );

            assertThat(jsonBody(response))
                    .containsEntry("message", "Username already exists");

            assertThat(users.findById(actor.getId()).orElseThrow().getUsername())
                    .isEqualTo("actor");

            assertThat(outbox.count()).isZero();
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "{\"email\":\"not-an-email\"}",
                "{\"username\":\"abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz\"}"
        })
        @DisplayName("Should return 400 and preserve data when the request is invalid")
        void updateMyProfileShouldReturnBadRequestWhenRequestIsInvalid(String body)
                throws Exception {
            perform(
                    patch(BASE + "/me")
                            .header(ACTOR_HEADER, actor.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body),
                    400
            );

            User unchangedUser = users.findById(actor.getId()).orElseThrow();

            assertThat(unchangedUser.getUsername()).isEqualTo("actor");
            assertThat(unchangedUser.getEmail()).isEqualTo("actor@example.com");
            assertThat(outbox.count()).isZero();
        }

        @Test
        @DisplayName("Should return 401 when the actor header is missing")
        void updateMyProfileShouldReturnUnauthorizedWhenActorHeaderIsMissing()
                throws Exception {
            perform(
                    patch(BASE + "/me")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"username":"renamed-actor"}
                                    """),
                    401
            );
        }
    }

    @Nested
    @DisplayName("DELETE /api/users/me")
    class DeleteMyAccount {

        @Test
        @DisplayName("Should delete only the authenticated user")
        void deleteMyAccountShouldDeleteAuthenticatedUser()
                throws Exception {
            perform(
                    delete(BASE + "/me")
                            .header(ACTOR_HEADER, actor.getId()),
                    204
            );

            assertThat(users.findById(actor.getId())).isEmpty();
            assertThat(users.findById(peer.getId())).isPresent();
        }

        @Test
        @DisplayName("Should return 401 when the actor header is missing")
        void deleteMyAccountShouldReturnUnauthorizedWhenActorHeaderIsMissing()
                throws Exception {
            perform(delete(BASE + "/me"), 401);

            assertThat(users.count()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("PATCH /api/users/me/password")
    class ChangeMyPassword {

        @Test
        @DisplayName("Should replace the password when the current password is correct")
        void changeMyPasswordShouldReplacePasswordWhenCurrentPasswordIsCorrect()
                throws Exception {
            perform(
                    patch(BASE + "/me/password")
                            .header(ACTOR_HEADER, actor.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(passwordBody(PASSWORD, NEW_PASSWORD)),
                    200
            );

            String updatedHash = users.findById(actor.getId())
                    .orElseThrow()
                    .getPassword();

            assertThat(passwordEncoder.matches(NEW_PASSWORD, updatedHash))
                    .isTrue();
            assertThat(passwordEncoder.matches(PASSWORD, updatedHash))
                    .isFalse();
        }

        @Test
        @DisplayName("Should return 400 and preserve the password when the current password is incorrect")
        void changeMyPasswordShouldPreservePasswordWhenCurrentPasswordIsIncorrect()
                throws Exception {
            String originalHash = users.findById(actor.getId())
                    .orElseThrow()
                    .getPassword();

            MockHttpServletResponse response = perform(
                    patch(BASE + "/me/password")
                            .header(ACTOR_HEADER, actor.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(passwordBody(
                                    "IncorrectPassword1!",
                                    NEW_PASSWORD
                            )),
                    400
            );

            assertThat(jsonBody(response))
                    .containsEntry("message", "Current password is incorrect");

            assertThat(users.findById(actor.getId()).orElseThrow().getPassword())
                    .isEqualTo(originalHash);
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "{\"currentPassword\":\" \",\"newPassword\":\"ReplacementPassword2!\"}",
                "{\"currentPassword\":\"FixturePassword1!\",\"newPassword\":\" \"}"
        })
        @DisplayName("Should return 400 and preserve the password when the request is invalid")
        void changeMyPasswordShouldPreservePasswordWhenRequestIsInvalid(String body)
                throws Exception {
            String originalHash = users.findById(actor.getId())
                    .orElseThrow()
                    .getPassword();

            perform(
                    patch(BASE + "/me/password")
                            .header(ACTOR_HEADER, actor.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body),
                    400
            );

            assertThat(users.findById(actor.getId()).orElseThrow().getPassword())
                    .isEqualTo(originalHash);
        }

        @Test
        @DisplayName("Should return 401 when the actor header is missing")
        void changeMyPasswordShouldReturnUnauthorizedWhenActorHeaderIsMissing()
                throws Exception {
            perform(
                    patch(BASE + "/me/password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(passwordBody(PASSWORD, NEW_PASSWORD)),
                    401
            );
        }
    }

    private User saveUser(String username, String email, String passwordHash) {
        return users.saveAndFlush(User.builder()
                .username(username)
                .email(email)
                .password(passwordHash)
                .role(UserRole.USER)
                .build());
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

    private static String passwordBody(String currentPassword, String newPassword) {
        return """
                {"currentPassword":"%s","newPassword":"%s"}
                """.formatted(currentPassword, newPassword);
    }
}
