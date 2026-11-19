package com.github.nhordiienko23.userservice.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.core.MethodParameter;

import java.lang.reflect.Method;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("Global exception handler tests")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler sut = new GlobalExceptionHandler();

    @Nested
    @DisplayName("handleAuthenticationException")
    class HandleAuthenticationException {

        @Test
        @DisplayName("handleAuthenticationException: should return 401 when authentication is invalid")
        void handleAuthenticationException_shouldReturn401_whenAuthenticationIsInvalid() {
            assertError(
                    sut.handleAuthenticationException(
                            new InvalidAuthenticationException("Invalid credentials"),
                            request("/api/users/login")
                    ),
                    HttpStatus.UNAUTHORIZED,
                    "Invalid credentials",
                    "/api/users/login"
            );
        }
    }

    @Nested
    @DisplayName("handleNotFound")
    class HandleNotFound {

        @Test
        @DisplayName("handleNotFound: should return 404 when the user does not exist")
        void handleNotFound_shouldReturn404_whenUserDoesNotExist() {
            assertError(
                    sut.handleNotFound(
                            new UserNotFoundException("User not found"),
                            request("/api/users/42")
                    ),
                    HttpStatus.NOT_FOUND,
                    "User not found",
                    "/api/users/42"
            );
        }
    }

    @Nested
    @DisplayName("handleConflict")
    class HandleConflict {

        @Test
        @DisplayName("handleConflict: should return 409 when the user already exists")
        void handleConflict_shouldReturn409_whenUserAlreadyExists() {
            assertError(
                    sut.handleConflict(
                            new UserAlreadyExistsException("User already exists"),
                            request("/api/users")
                    ),
                    HttpStatus.CONFLICT,
                    "User already exists",
                    "/api/users"
            );
        }
    }

    @Nested
    @DisplayName("handleUnauthenticated")
    class HandleUnauthenticated {

        @Test
        @DisplayName("handleUnauthenticated: should return 401 when authentication is required")
        void handleUnauthenticated_shouldReturn401_whenAuthenticationIsRequired() {
            assertError(
                    sut.handleUnauthenticated(
                            new UnauthenticatedException("Authentication is required"),
                            request("/api/users/me")
                    ),
                    HttpStatus.UNAUTHORIZED,
                    "Authentication is required",
                    "/api/users/me"
            );
        }
    }

    @Nested
    @DisplayName("handleForbidden")
    class HandleForbidden {

        @Test
        @DisplayName("handleForbidden: should return 403 when the operation is forbidden")
        void handleForbidden_shouldReturn403_whenOperationIsForbidden() {
            assertError(
                    sut.handleForbidden(
                            new ForbiddenOperationException("Administrator role required"),
                            request("/api/users")
                    ),
                    HttpStatus.FORBIDDEN,
                    "Administrator role required",
                    "/api/users"
            );
        }
    }

    @Nested
    @DisplayName("handleInvalidArgument")
    class HandleInvalidArgument {

        @Test
        @DisplayName("handleInvalidArgument: should return 400 and preserve the message")
        void handleInvalidArgument_shouldReturn400_whenArgumentIsInvalid() {
            String message = "用户 🔒: invalid input";

            assertError(
                    sut.handleInvalidArgument(
                            new InvalidUserArgumentException(message),
                            request("/api/users")
                    ),
                    HttpStatus.BAD_REQUEST,
                    message,
                    "/api/users"
            );
        }
    }

    @Nested
    @DisplayName("handleValidation")
    class HandleValidation {

        @Test
        @DisplayName("handleValidation: should return distinct field errors when validation fails")
        void handleValidation_shouldReturnDistinctFieldErrors_whenFieldsAreInvalid() throws Exception {
            BeanPropertyBindingResult bindingResult =
                    new BeanPropertyBindingResult(new Object(), "request");
            bindingResult.addError(new FieldError(
                    "request", "email", null, false, null, null, "must be valid"
            ));
            bindingResult.addError(new FieldError(
                    "request", "email", null, false, null, null, "must be valid"
            ));
            bindingResult.addError(new FieldError(
                    "request", "name", null, false, null, null, "must not be blank"
            ));

            MethodArgumentNotValidException exception =
                    new MethodArgumentNotValidException(validationMethodParameter(), bindingResult);

            assertError(
                    sut.handleValidation(exception, request("/api/users")),
                    HttpStatus.BAD_REQUEST,
                    "email: must be valid; name: must not be blank",
                    "/api/users"
            );
        }

        @Test
        @DisplayName("handleValidation: should use the fallback message when there are no field errors")
        void handleValidation_shouldUseFallbackMessage_whenThereAreNoFieldErrors() throws Exception {
            BeanPropertyBindingResult bindingResult =
                    new BeanPropertyBindingResult(new Object(), "request");
            MethodArgumentNotValidException exception =
                    new MethodArgumentNotValidException(validationMethodParameter(), bindingResult);

            assertError(
                    sut.handleValidation(exception, request("/api/users")),
                    HttpStatus.BAD_REQUEST,
                    "Request validation failed",
                    "/api/users"
            );
        }
    }

    @Nested
    @DisplayName("handleTypeMismatch")
    class HandleTypeMismatch {

        @Test
        @DisplayName("handleTypeMismatch: should return a generic 400 when a parameter has an invalid type")
        void handleTypeMismatch_shouldReturnGeneric400_whenParameterTypeIsInvalid() {
            assertError(
                    sut.handleTypeMismatch(
                            mock(MethodArgumentTypeMismatchException.class),
                            request("/api/users/not-a-number")
                    ),
                    HttpStatus.BAD_REQUEST,
                    "A request parameter or path variable has an invalid type",
                    "/api/users/not-a-number"
            );
        }
    }

    @Nested
    @DisplayName("handleMethodNotAllowed")
    class HandleMethodNotAllowed {

        @Test
        @DisplayName("handleMethodNotAllowed: should return 405 when the HTTP method is unsupported")
        void handleMethodNotAllowed_shouldReturn405_whenHttpMethodIsUnsupported() {
            assertError(
                    sut.handleMethodNotAllowed(
                            new HttpRequestMethodNotSupportedException("PATCH", Collections.singleton("GET")),
                            request("/api/users")
                    ),
                    HttpStatus.METHOD_NOT_ALLOWED,
                    "HTTP method is not supported for this endpoint",
                    "/api/users"
            );
        }
    }

    @Nested
    @DisplayName("handleUnreadableBody")
    class HandleUnreadableBody {

        @Test
        @DisplayName("handleUnreadableBody: should return 400 when the request body is malformed")
        void handleUnreadableBody_shouldReturn400_whenRequestBodyIsMalformed() {
            assertError(
                    sut.handleUnreadableBody(
                            mock(HttpMessageNotReadableException.class),
                            request("/api/users")
                    ),
                    HttpStatus.BAD_REQUEST,
                    "Request body is missing or malformed",
                    "/api/users"
            );
        }
    }

    @Nested
    @DisplayName("handleDataConflict")
    class HandleDataConflict {

        @Test
        @DisplayName("handleDataConflict: should return a generic 409 when data violates a constraint")
        void handleDataConflict_shouldReturnGeneric409_whenDataConstraintIsViolated() {
            assertError(
                    sut.handleDataConflict(
                            new DataIntegrityViolationException("Sensitive database details"),
                            request("/api/users")
                    ),
                    HttpStatus.CONFLICT,
                    "The change conflicts with existing data",
                    "/api/users"
            );
        }
    }

    @Nested
    @DisplayName("handleUnexpected")
    class HandleUnexpected {

        @Test
        @DisplayName("handleUnexpected: should return a generic 500 without exposing exception details")
        void handleUnexpected_shouldReturnGeneric500_whenUnexpectedExceptionOccurs() {
            assertError(
                    sut.handleUnexpected(
                            new IllegalStateException("Sensitive internal details"),
                            request("/api/users")
                    ),
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "An unexpected error occurred",
                    "/api/users"
            );
        }
    }

    private static void assertError(
            ResponseEntity<ApiError> response,
            HttpStatus status,
            String message,
            String path
    ) {
        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(response.getBody()).isNotNull().satisfies(body -> {
            assertThat(body.timestamp()).isNotNull();
            assertThat(body.status()).isEqualTo(status.value());
            assertThat(body.error()).isEqualTo(status.getReasonPhrase());
            assertThat(body.message()).isEqualTo(message);
            assertThat(body.path()).isEqualTo(path);
        });
    }

    private static MockHttpServletRequest request(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(path);
        return request;
    }

    private static MethodParameter validationMethodParameter() throws NoSuchMethodException {
        Method method = GlobalExceptionHandlerTest.class
                .getDeclaredMethod("validationEndpoint", String.class);
        return new MethodParameter(method, 0);
    }

    private void validationEndpoint(String ignored) {
    }
}