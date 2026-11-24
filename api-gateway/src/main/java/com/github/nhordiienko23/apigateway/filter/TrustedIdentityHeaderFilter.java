package com.github.nhordiienko23.apigateway.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;

public class TrustedIdentityHeaderFilter extends OncePerRequestFilter {

    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String USER_ROLE_HEADER = "X-User-Role";

    private static final String USER_PREFIX = "/api/users/**";
    private static final String ADMIN_USERS_PREFIX = "/api/admin/users/**";

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!isForwardingRoute(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        Map<String, List<String>> replacementHeaders = new LinkedHashMap<>();
        if (!isPublicRequest(request) || isEngagementSummaryRequest(request)) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
                if (requiresUserId(request)) {
                    replacementHeaders.put(USER_ID_HEADER,
                            List.of(jwtAuthentication.getToken().getSubject()));
                }
                if (requiresUserRole(request)) {
                    replacementHeaders.put(USER_ROLE_HEADER,
                            List.of(jwtAuthentication.getToken().getClaimAsString("role")));
                }
            }
        }

        filterChain.doFilter(new SanitizedHeaderRequest(request, replacementHeaders), response);
    }

    private boolean isForwardingRoute(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/auth/")
                || pathMatcher.match("/api/users/**", path)
                || pathMatcher.match("/api/admin/users/**", path)
                || pathMatcher.match("/api/posts/**", path)
                || pathMatcher.match("/api/leaderboard/**", path);
    }

    private boolean isPublicRequest(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();

        if (HttpMethod.POST.matches(method) && isOneOf(path,
                "/api/auth/register", "/api/auth/login", "/api/auth/refresh", "/api/auth/logout")) {
            return true;
        }

        if (HttpMethod.GET.matches(method)) {
            if (pathMatcher.match("/api/users/{id}/summary", path)
                    || pathMatcher.match("/api/posts", path)
                    || pathMatcher.match("/api/posts/{id}", path)
                    || pathMatcher.match("/api/posts/{postId}/comments", path)
                    || pathMatcher.match("/api/posts/{postId}/comments/{commentId}", path)
                    || isEngagementSummaryRequest(request)
                    || pathMatcher.match("/api/leaderboard/top-10", path)
                    || pathMatcher.match("/api/leaderboard/top-100", path)) {
                return true;
            }
        }
        return false;
    }

    private boolean requiresUserId(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();

        if (isEngagementSummaryRequest(request)) {
            return true;
        }

        if (HttpMethod.GET.matches(method)
                && pathMatcher.match("/api/posts/users/{userId}/activity", path)) {
            return true;
        }

        if (pathMatcher.match(USER_PREFIX, path)
                || pathMatcher.match(ADMIN_USERS_PREFIX, path)) {
            return true;
        }

        if (pathMatcher.match("/api/posts/{id}", path)
                && isOneOf(method, HttpMethod.PUT.name(), HttpMethod.DELETE.name())) {
            return true;
        }
        if (HttpMethod.POST.matches(method) && pathMatcher.match("/api/posts", path)) {
            return true;
        }
        if (pathMatcher.match("/api/posts/{postId}/likes", path)
                && isOneOf(method, HttpMethod.POST.name(), HttpMethod.DELETE.name())) {
            return true;
        }
        if (pathMatcher.match("/api/posts/{postId}/comments/{commentId}/likes", path)
                && isOneOf(method, HttpMethod.POST.name(), HttpMethod.DELETE.name())) {
            return true;
        }
        if (pathMatcher.match("/api/posts/{postId}/comments", path)
                && HttpMethod.POST.matches(method)) {
            return true;
        }
        return pathMatcher.match("/api/posts/{postId}/comments/{commentId}", path)
                && isOneOf(method, HttpMethod.PUT.name(), HttpMethod.DELETE.name());
    }

    private boolean requiresUserRole(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();

        if (pathMatcher.match("/api/posts/{id}", path)
                && isOneOf(method, HttpMethod.PUT.name(), HttpMethod.DELETE.name())) {
            return true;
        }
        return pathMatcher.match("/api/posts/{postId}/comments/{commentId}", path)
                && isOneOf(method, HttpMethod.PUT.name(), HttpMethod.DELETE.name());
    }

    private boolean isEngagementSummaryRequest(HttpServletRequest request) {
        if (!HttpMethod.GET.matches(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        return pathMatcher.match("/api/posts/{postId}/engagement", path)
                || pathMatcher.match("/api/posts/{postId}/comments/{commentId}/engagement", path);
    }

    private boolean isOneOf(String value, String... candidates) {
        for (String candidate : candidates) {
            if (candidate.equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    private static final class SanitizedHeaderRequest extends HttpServletRequestWrapper {

        private final Map<String, List<String>> replacements;

        private SanitizedHeaderRequest(HttpServletRequest request,
                                       Map<String, List<String>> replacements) {
            super(request);
            this.replacements = replacements;
        }

        @Override
        public String getHeader(String name) {
            if (isIdentityHeader(name)) {
                List<String> values = replacements.get(toCaseInsensitiveKey(name));
                if (values == null || values.isEmpty()) {
                    return null;
                }
                return values.get(0);
            }
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if (isIdentityHeader(name)) {
                List<String> values = replacements.get(toCaseInsensitiveKey(name));
                return Collections.enumeration(values == null ? Collections.emptyList() : values);
            }
            return super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            List<String> names = new ArrayList<>();
            Enumeration<String> originalNames = super.getHeaderNames();
            while (originalNames.hasMoreElements()) {
                String name = originalNames.nextElement();
                if (!isIdentityHeader(name)) {
                    names.add(name);
                }
            }
            names.addAll(replacements.keySet());
            return Collections.enumeration(names);
        }

        private boolean isIdentityHeader(String name) {
            return USER_ID_HEADER.equalsIgnoreCase(name) || USER_ROLE_HEADER.equalsIgnoreCase(name);
        }

        private String toCaseInsensitiveKey(String name) {
            for (String key : replacements.keySet()) {
                if (key.equalsIgnoreCase(name)) {
                    return key;
                }
            }
            return name;
        }
    }
}
