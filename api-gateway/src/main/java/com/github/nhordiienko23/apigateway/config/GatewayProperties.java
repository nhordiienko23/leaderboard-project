package com.github.nhordiienko23.apigateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties("gateway")
public record GatewayProperties(
        Jwt jwt,
        DownstreamServices downstreamServices,
        Cors cors
) {
    public record Jwt(
            String issuer,
            String audience,
            String publicKeyLocation
    ) {
    }

    public record DownstreamServices(
            String userService,
            String postService,
            String leaderboardService
    ) {
    }

    public record Cors(
            List<String> allowedOrigins,
            List<String> allowedMethods,
            List<String> allowedHeaders
    ) {
    }
}

