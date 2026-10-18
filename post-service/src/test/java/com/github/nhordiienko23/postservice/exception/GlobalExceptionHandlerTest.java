package com.github.nhordiienko23.postservice.exception;

import com.github.nhordiienko23.postservice.exception.accessdenied.AccessDeniedException;
import com.github.nhordiienko23.postservice.exception.accessdenied.PostAccessDeniedException;
import com.github.nhordiienko23.postservice.exception.notfound.PostNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
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
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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

        ApiErrorResponse body = response.getBody();
        assertNotNull(body);

        assertEquals(403, body.status());
        assertEquals("Forbidden", body.error());
        assertEquals(
                "You are not allowed to modify this post",
                body.message()
        );
        assertEquals("/api/posts/42", body.path());
        assertNotNull(body.timestamp());
        assertTrue(body.fieldErrors().isEmpty());
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

        ApiErrorResponse body = response.getBody();
        assertNotNull(body);

        assertEquals(404, body.status());
        assertEquals("Not Found", body.error());
        assertEquals("Post with id=42 not found", body.message());
        assertEquals("/api/posts/42", body.path());
        assertTrue(body.fieldErrors().isEmpty());
    }

    @Test
    void handleNoResourceFound_shouldReturn404() {
        HttpServletRequest request =
                createRequest("GET", "/api/unknown");

        var response = handler.handleNoResourceFound(request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());

        ApiErrorResponse body = response.getBody();
        assertNotNull(body);

        assertEquals(404, body.status());
        assertEquals(
                "The requested resource was not found.",
                body.message()
        );
        assertEquals("/api/unknown", body.path());
        assertTrue(body.fieldErrors().isEmpty());
    }

    // -------------------------------------------------------------------------
    // BEAN VALIDATION
    // -------------------------------------------------------------------------

    @Test
    void handleValidation_shouldReturn400AndMergeFieldErrors()
            throws Exception {

        HttpServletRequest request =
                createRequest("POST", "/api/posts");

        TestRequest target = new TestRequest();
        target.setContent("");

        BeanPropertyBindingResult bindingResult =
                new BeanPropertyBindingResult(target, "request");

        bindingResult.rejectValue(
                "content",
                "NotBlank",
                "must not be blank"
        );

        bindingResult.rejectValue(
                "content",
                "Size",
                "must not exceed 255 characters"
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

        ApiErrorResponse body = response.getBody();
        assertNotNull(body);

        assertEquals(400, body.status());
        assertEquals("Request validation failed.", body.message());

        assertEquals(
                "must not be blank; must not exceed 255 characters",
                body.fieldErrors().get("content")
        );

        assertEquals(
                "Request contains invalid fields",
                body.fieldErrors().get("_global")
        );

        assertEquals("/api/posts", body.path());
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

        ApiErrorResponse body = response.getBody();
        assertNotNull(body);

        assertEquals(400, body.status());
        assertEquals("Request validation failed.", body.message());
        assertTrue(body.fieldErrors().isEmpty());
        assertEquals("/api/posts", body.path());
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

        ApiErrorResponse body = response.getBody();
        assertNotNull(body);

        assertEquals(400, body.status());
        assertEquals("Request validation failed.", body.message());
        assertEquals(
                "must not be null",
                body.fieldErrors().get("createPost.userId")
        );
    }

    // -------------------------------------------------------------------------
    // REQUEST BODY AND PARAMETERS
    // -------------------------------------------------------------------------

    @Test
    void handleInvalidRequestBody_shouldReturn400() {
        HttpServletRequest request =
                createRequest("POST", "/api/posts");

        var response = handler.handleInvalidRequestBody(request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());

        ApiErrorResponse body = response.getBody();
        assertNotNull(body);

        assertEquals(
                "Request body is missing or malformed.",
                body.message()
        );
        assertEquals("/api/posts", body.path());
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

        ApiErrorResponse body = response.getBody();
        assertNotNull(body);

        assertEquals(
                "Invalid value for parameter 'postId'.",
                body.message()
        );
        assertEquals("/api/posts/abc", body.path());
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

        ApiErrorResponse body = response.getBody();
        assertNotNull(body);

        assertEquals(
                "Required parameter 'page' is missing.",
                body.message()
        );
        assertEquals("/api/posts", body.path());
    }

    @Test
    void handleMissingRequestHeader_shouldReturn400() {
        HttpServletRequest request =
                createRequest("POST", "/api/posts");

        MissingRequestHeaderException exception =
                mock(MissingRequestHeaderException.class);

        when(exception.getHeaderName()).thenReturn("X-User-Id");

        var response =
                handler.handleMissingRequestHeader(exception, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());

        Map<String, Object> body = response.getBody();
        assertNotNull(body);

        assertEquals(400, body.get("status"));
        assertEquals("Bad Request", body.get("error"));
        assertEquals(
                "Required request header 'X-User-Id' is missing",
                body.get("message")
        );
        assertEquals("/api/posts", body.get("path"));
        assertEquals(Map.of(), body.get("fieldErrors"));
        assertInstanceOf(Instant.class, body.get("timestamp"));
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

        ApiErrorResponse body = response.getBody();
        assertNotNull(body);

        assertEquals(
                "HTTP method is not supported for this endpoint.",
                body.message()
        );
        assertEquals("/api/posts", body.path());
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

        ApiErrorResponse body = response.getBody();
        assertNotNull(body);

        assertEquals(
                "The request content type is not supported.",
                body.message()
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

        ApiErrorResponse body = response.getBody();
        assertNotNull(body);

        assertEquals(
                "The requested response format is not supported.",
                body.message()
        );
    }

    // -------------------------------------------------------------------------
    // OPTIMISTIC LOCKING AND RESPONSE STATUS
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

        ApiErrorResponse body = response.getBody();
        assertNotNull(body);

        assertEquals(
                "The resource was modified by another request. Please retry.",
                body.message()
        );
        assertEquals("/api/posts/42", body.path());
    }

    @Test
    void handleResponseStatus_shouldPreserveStatusAndReason() {
        HttpServletRequest request =
                createRequest("POST", "/api/posts");

        ResponseStatusException exception =
                new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "You have already liked this post"
                );

        var response = handler.handleResponseStatus(exception, request);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());

        ApiErrorResponse body = response.getBody();
        assertNotNull(body);

        assertEquals(409, body.status());
        assertEquals(
                "You have already liked this post",
                body.message()
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

        ApiErrorResponse body = response.getBody();
        assertNotNull(body);

        assertEquals("Not Found", body.message());
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

        ApiErrorResponse body = response.getBody();
        assertNotNull(body);

        assertEquals(500, body.status());
        assertEquals(
                "An unexpected error occurred. Please try again later.",
                body.message()
        );
        assertFalse(body.message().contains("Sensitive"));
        assertEquals("/api/posts", body.path());
        assertNotNull(body.timestamp());
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
    private void validationEndpoint(TestRequest request) {
    }

    private static class TestRequest {

        private String content;

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }
}