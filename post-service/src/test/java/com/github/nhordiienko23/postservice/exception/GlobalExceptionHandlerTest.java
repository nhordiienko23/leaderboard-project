package com.github.nhordiienko23.postservice.exception;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.core.MethodParameter;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Global Exception Handler Tests")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler exceptionHandler =
            new GlobalExceptionHandler();

    @Test
    @DisplayName("Should return 404 when a resource is not found")
    void shouldReturn404WhenResourceNotFound() {
        MockHttpServletRequest request =
                createRequest("GET", "/api/posts/42");

        PostNotFoundException exception =
                new PostNotFoundException("Post with id=42 not found");

        var response = exceptionHandler.handleNotFound(exception, request);

        assertEquals(HttpStatus.NOT_FOUND.value(),
                response.getStatusCode().value());

        assertNotNull(response.getBody());
        assertEquals(HttpStatus.NOT_FOUND.value(),
                response.getBody().status());
        assertEquals("Not Found", response.getBody().error());
        assertEquals("Post with id=42 not found",
                response.getBody().message());
        assertEquals("/api/posts/42", response.getBody().path());
        assertNotNull(response.getBody().timestamp());
        assertTrue(response.getBody().fieldErrors().isEmpty());
    }

    @Test
    @DisplayName("Should return 400 with field errors for invalid input")
    void shouldReturn400WhenValidationFails() throws Exception {
        MockHttpServletRequest request =
                createRequest("POST", "/api/posts");

        TestRequest requestBody = new TestRequest();
        requestBody.setContent("");

        BeanPropertyBindingResult bindingResult =
                new BeanPropertyBindingResult(requestBody, "request");

        bindingResult.rejectValue(
                "content",
                "NotBlank",
                "must not be blank"
        );

        Method method = getClass().getDeclaredMethod(
                "validationEndpoint",
                TestRequest.class
        );

        MethodArgumentNotValidException exception =
                new MethodArgumentNotValidException(
                        new MethodParameter(method, 0),
                        bindingResult
                );

        var response =
                exceptionHandler.handleValidation(exception, request);

        assertEquals(HttpStatus.BAD_REQUEST.value(),
                response.getStatusCode().value());

        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().status());
        assertEquals("Bad Request", response.getBody().error());
        assertEquals("Request validation failed.",
                response.getBody().message());
        assertEquals("/api/posts", response.getBody().path());
        assertEquals(
                "must not be blank",
                response.getBody().fieldErrors().get("content")
        );
    }

    @Test
    @DisplayName("Should return 500 without exposing internal error details")
    void shouldReturn500WhenUnexpectedExceptionOccurs() {
        MockHttpServletRequest request =
                createRequest("GET", "/api/posts");

        Exception exception = new IllegalStateException(
                "Internal database credentials must remain private"
        );

        var response =
                exceptionHandler.handleUnexpectedException(exception, request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(),
                response.getStatusCode().value());

        assertNotNull(response.getBody());
        assertEquals(500, response.getBody().status());
        assertEquals("Internal Server Error",
                response.getBody().error());
        assertEquals(
                "An unexpected error occurred. Please try again later.",
                response.getBody().message()
        );
        assertEquals("/api/posts", response.getBody().path());

        assertFalse(response.getBody().message()
                .contains("credentials"));
    }

    @Test
    @DisplayName("Should return 409 when concurrent modification occurs")
    void shouldReturn409WhenOptimisticLockingFails() {
        MockHttpServletRequest request =
                createRequest("PUT", "/api/posts/42");

        OptimisticLockingFailureException exception =
                new OptimisticLockingFailureException(
                        "Concurrent database update"
                );

        var response = exceptionHandler.handleOptimisticLocking(
                exception,
                request
        );

        assertEquals(HttpStatus.CONFLICT.value(),
                response.getStatusCode().value());

        assertNotNull(response.getBody());
        assertEquals(409, response.getBody().status());
        assertEquals("Conflict", response.getBody().error());
        assertEquals(
                "The resource was modified by another request. Please retry.",
                response.getBody().message()
        );
        assertEquals("/api/posts/42", response.getBody().path());
    }

    @Test
    @DisplayName("Should return 400 when request body is malformed")
    void shouldReturn400WhenRequestBodyIsMalformed() {
        MockHttpServletRequest request =
                createRequest("POST", "/api/posts");

        var response =
                exceptionHandler.handleInvalidRequestBody(request);

        assertEquals(HttpStatus.BAD_REQUEST.value(),
                response.getStatusCode().value());

        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().status());
        assertEquals(
                "Request body is missing or malformed.",
                response.getBody().message()
        );
        assertEquals("/api/posts", response.getBody().path());
    }

    private MockHttpServletRequest createRequest(
            String method,
            String uri
    ) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod(method);
        request.setRequestURI(uri);
        return request;
    }

    @SuppressWarnings("unused")
    private void validationEndpoint(@Valid TestRequest request) {
    }

    private static class TestRequest {

        @NotBlank
        private String content;

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }
}
