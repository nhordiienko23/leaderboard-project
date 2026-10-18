package com.github.nhordiienko23.postservice.exception;

import com.github.nhordiienko23.postservice.exception.accessdenied.AccessDeniedException;
import com.github.nhordiienko23.postservice.exception.accessdenied.PostAccessDeniedException;
import com.github.nhordiienko23.postservice.exception.notfound.PostNotFoundException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    // -------------------------------------------------------------------------
    // ACCESS DENIED
    // -------------------------------------------------------------------------

    @Test
    void handleAccessDenied_shouldReturn403() {
        HttpServletRequest request =
                createRequest("DELETE", "/api/posts/42");

        AccessDeniedException exception =
                new PostAccessDeniedException(
                        "You are not allowed to modify this post"
                );

        var response = handler.handleAccessDenied(exception, request);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(403, response.getBody().status());
        assertEquals("Forbidden", response.getBody().error());
        assertEquals(
                "You are not allowed to modify this post",
                response.getBody().message()
        );
        assertEquals("/api/posts/42", response.getBody().path());
        assertNotNull(response.getBody().timestamp());
        assertTrue(response.getBody().fieldErrors().isEmpty());
    }

    // -------------------------------------------------------------------------
    // NOT FOUND
    // -------------------------------------------------------------------------

    @Test
    void handleNotFound_shouldReturn404() {
        HttpServletRequest request =
                createRequest("GET", "/api/posts/42");

        PostNotFoundException exception =
                new PostNotFoundException("Post with id=42 not found");

        var response = handler.handleNotFound(exception, request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(404, response.getBody().status());
        assertEquals("Not Found", response.getBody().error());
        assertEquals(
                "Post with id=42 not found",
                response.getBody().message()
        );
        assertEquals("/api/posts/42", response.getBody().path());
    }

    @Test
    void handleNoResourceFound_shouldReturn404() {
        HttpServletRequest request =
                createRequest("GET", "/api/unknown");

        var response = handler.handleNoResourceFound(request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(404, response.getBody().status());
        assertEquals(
                "The requested resource was not found.",
                response.getBody().message()
        );
        assertEquals("/api/unknown", response.getBody().path());
    }

    // -------------------------------------------------------------------------
    // BEAN VALIDATION
    // -------------------------------------------------------------------------

    @Test
    void handleValidation_shouldReturn400WithFieldErrors()
            throws Exception {

        HttpServletRequest request =
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

        bindingResult.rejectValue(
                "content",
                "Size",
                "too short"
        );

        bindingResult.reject(
                "invalid.request",
                "Request contains invalid fields"
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

        var response = handler.handleValidation(exception, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().status());
        assertEquals("Request validation failed.", response.getBody().message());

        assertEquals(
                "must not be blank; too short",
                response.getBody().fieldErrors().get("content")
        );

        assertEquals(
                "Request contains invalid fields",
                response.getBody().fieldErrors().get("_global")
        );
    }

    @Test
    void handleMethodValidation_shouldReturn400() {
        HttpServletRequest request =
                createRequest("GET", "/api/posts");

        HandlerMethodValidationException exception =
                mock(HandlerMethodValidationException.class);

        when(exception.getParameterValidationResults())
                .thenReturn(List.of());
        when(exception.getCrossParameterValidationResults())
                .thenReturn(List.of());

        var response = handler.handleMethodValidation(exception, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(400, response.getBody().status());
        assertEquals("Request validation failed.", response.getBody().message());
        assertTrue(response.getBody().fieldErrors().isEmpty());
    }

    @Test
    void handleConstraintViolation_shouldReturn400WithViolationDetails() {
        HttpServletRequest request =
                createRequest("GET", "/api/posts");

        @SuppressWarnings("unchecked")
        ConstraintViolation<Object> violation =
                mock(ConstraintViolation.class);

        Path path = mock(Path.class);

        when(violation.getPropertyPath()).thenReturn(path);
        when(path.toString()).thenReturn("createPost.userId");
        when(violation.getMessage()).thenReturn("must not be null");

        ConstraintViolationException exception =
                new ConstraintViolationException(Set.of(violation));

        var response =
                handler.handleConstraintViolation(exception, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(
                "must not be null",
                response.getBody().fieldErrors().get("createPost.userId")
        );
    }

    // -------------------------------------------------------------------------
    // INVALID REQUEST
    // -------------------------------------------------------------------------

    @Test
    void handleInvalidRequestBody_shouldReturn400() {
        HttpServletRequest request =
                createRequest("POST", "/api/posts");

        var response = handler.handleInvalidRequestBody(request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(
                "Request body is missing or malformed.",
                response.getBody().message()
        );
        assertEquals("/api/posts", response.getBody().path());
    }

    @Test
    void handleTypeMismatch_shouldReturn400WithParameterName() {
        HttpServletRequest request =
                createRequest("GET", "/api/posts/abc");

        MethodArgumentTypeMismatchException exception =
                mock(MethodArgumentTypeMismatchException.class);

        when(exception.getName()).thenReturn("postId");

        var response = handler.handleTypeMismatch(exception, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(
                "Invalid value for parameter 'postId'.",
                response.getBody().message()
        );
    }

    @Test
    void handleMissingParameter_shouldReturn400WithParameterName() {
        HttpServletRequest request =
                createRequest("GET", "/api/posts");

        MissingServletRequestParameterException exception =
                new MissingServletRequestParameterException(
                        "page",
                        "int"
                );

        var response = handler.handleMissingParameter(exception, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(
                "Required parameter 'page' is missing.",
                response.getBody().message()
        );
    }

    // -------------------------------------------------------------------------
    // HTTP METHOD AND MEDIA TYPE
    // -------------------------------------------------------------------------

    @Test
    void handleMethodNotSupported_shouldReturn405() {
        HttpServletRequest request =
                createRequest("PATCH", "/api/posts");

        var response = handler.handleMethodNotSupported(request);

        assertEquals(
                HttpStatus.METHOD_NOT_ALLOWED,
                response.getStatusCode()
        );
        assertNotNull(response.getBody());
        assertEquals(
                "HTTP method is not supported for this endpoint.",
                response.getBody().message()
        );
    }

    @Test
    void handleUnsupportedMediaType_shouldReturn415() {
        HttpServletRequest request =
                createRequest("POST", "/api/posts");

        var response = handler.handleUnsupportedMediaType(request);

        assertEquals(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                response.getStatusCode()
        );
        assertNotNull(response.getBody());
        assertEquals(
                "The request content type is not supported.",
                response.getBody().message()
        );
    }

    @Test
    void handleNotAcceptable_shouldReturn406() {
        HttpServletRequest request =
                createRequest("GET", "/api/posts");

        var response = handler.handleNotAcceptable(request);

        assertEquals(
                HttpStatus.NOT_ACCEPTABLE,
                response.getStatusCode()
        );
        assertNotNull(response.getBody());
        assertEquals(
                "The requested response format is not supported.",
                response.getBody().message()
        );
    }

    // -------------------------------------------------------------------------
    // CONFLICT AND RESPONSE STATUS
    // -------------------------------------------------------------------------

    @Test
    void handleOptimisticLocking_shouldReturn409() {
        HttpServletRequest request =
                createRequest("PUT", "/api/posts/42");

        OptimisticLockingFailureException exception =
                new OptimisticLockingFailureException(
                        "Concurrent database update"
                );

        var response =
                handler.handleOptimisticLocking(exception, request);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(
                "The resource was modified by another request. Please retry.",
                response.getBody().message()
        );
    }

    @Test
    void handleResponseStatus_shouldUseExceptionStatusAndReason() {
        HttpServletRequest request =
                createRequest("POST", "/api/posts");

        ResponseStatusException exception =
                new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "You have already liked this post"
                );

        var response = handler.handleResponseStatus(exception, request);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(409, response.getBody().status());
        assertEquals(
                "You have already liked this post",
                response.getBody().message()
        );
    }

    @Test
    void handleResponseStatus_shouldUseStatusPhraseWhenReasonIsMissing() {
        HttpServletRequest request =
                createRequest("GET", "/api/posts");

        ResponseStatusException exception =
                new ResponseStatusException(HttpStatus.NOT_FOUND);

        var response = handler.handleResponseStatus(exception, request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Not Found", response.getBody().message());
    }

    // -------------------------------------------------------------------------
    // UNEXPECTED ERRORS
    // -------------------------------------------------------------------------

    @Test
    void handleUnexpectedException_shouldReturn500WithoutLeakingDetails() {
        HttpServletRequest request =
                createRequest("GET", "/api/posts");

        Exception exception = new IllegalStateException(
                "Sensitive internal database details"
        );

        var response =
                handler.handleUnexpectedException(exception, request);

        assertEquals(
                HttpStatus.INTERNAL_SERVER_ERROR,
                response.getStatusCode()
        );
        assertNotNull(response.getBody());
        assertEquals(500, response.getBody().status());
        assertEquals(
                "An unexpected error occurred. Please try again later.",
                response.getBody().message()
        );
        assertFalse(
                response.getBody().message().contains("Sensitive")
        );
        assertEquals("/api/posts", response.getBody().path());
    }

    // -------------------------------------------------------------------------
    // HELPERS
    // -------------------------------------------------------------------------

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