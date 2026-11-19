package com.github.nhordiienko23.apigateway.config;

import com.github.nhordiienko23.apigateway.filter.TrustedIdentityHeaderFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final GatewayProperties gatewayProperties;

    public SecurityConfig(GatewayProperties gatewayProperties) {
        this.gatewayProperties = gatewayProperties;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.POST,
                                "/api/auth/register",
                                "/api/auth/login",
                                "/api/auth/refresh",
                                "/api/auth/logout").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/posts",
                                "/api/posts/{id}",
                                "/api/posts/{postId}/comments",
                                "/api/posts/{postId}/comments/{commentId}",
                                "/api/leaderboard/top-10",
                                "/api/leaderboard/top-100").permitAll()
                        .requestMatchers("/api/admin/users/**").access(adminRoleManager())
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> {})
                )
                .addFilterAfter(new TrustedIdentityHeaderFilter(), BearerTokenAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        GatewayProperties.Cors corsProperties = gatewayProperties.cors();
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsProperties.allowedOrigins() == null
                ? List.of()
                : corsProperties.allowedOrigins());
        configuration.setAllowedMethods(corsProperties.allowedMethods() == null
                ? List.of()
                : corsProperties.allowedMethods());
        configuration.setAllowedHeaders(corsProperties.allowedHeaders() == null
                ? List.of()
                : corsProperties.allowedHeaders());
        configuration.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    private AuthorizationManager<RequestAuthorizationContext> adminRoleManager() {
        return (authentication, context) -> {
            Authentication currentAuthentication = authentication.get();
            if (currentAuthentication == null) {
                return new AuthorizationDecision(false);
            }

            if (!(currentAuthentication instanceof JwtAuthenticationToken jwtAuthentication)) {
                return new AuthorizationDecision(false);
            }

            String role = jwtAuthentication.getToken().getClaimAsString("role");
            return new AuthorizationDecision("ADMIN".equals(role));
        };
    }
}
