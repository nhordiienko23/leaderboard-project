package com.github.nhordiienko23.apigateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Collection;

@Configuration
public class JwtSecurityConfig {

    private final GatewayProperties gatewayProperties;
    private final ResourceLoader resourceLoader;

    public JwtSecurityConfig(GatewayProperties gatewayProperties, ResourceLoader resourceLoader) {
        this.gatewayProperties = gatewayProperties;
        this.resourceLoader = resourceLoader;
    }

    @Bean
    public JwtDecoder jwtDecoder() throws Exception {
        Resource publicKeyResource = resourceLoader.getResource(
                gatewayProperties.jwt().publicKeyLocation()
        );
        RSAPublicKey publicKey = loadPublicKey(publicKeyResource);

        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey).build();
        OAuth2TokenValidator<Jwt> issuerValidator = JwtValidators.createDefaultWithIssuer(
                gatewayProperties.jwt().issuer()
        );
        OAuth2TokenValidator<Jwt> expirationClaimValidator = new JwtClaimValidator<>(
                "exp",
                expiration -> expiration != null
        );

        OAuth2TokenValidator<Jwt> audienceValidator = new JwtClaimValidator<>(
                "aud",
                audience -> containsAudience(audience, gatewayProperties.jwt().audience())
        );
        OAuth2TokenValidator<Jwt> subjectValidator = new JwtClaimValidator<>(
                "sub",
                JwtSecurityConfig::isPositiveNumericSubject
        );
        OAuth2TokenValidator<Jwt> roleValidator = new JwtClaimValidator<>(
                "role",
                role -> "USER".equals(role) || "ADMIN".equals(role)
        );

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                issuerValidator,
                expirationClaimValidator,
                audienceValidator,
                subjectValidator,
                roleValidator
        ));
        return decoder;
    }

    private RSAPublicKey loadPublicKey(Resource resource) throws Exception {
        String pem;
        try (InputStream input = resource.getInputStream()) {
            pem = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        String encodedKey = pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");

        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(encodedKey);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Configured RSA public key is not valid Base64", exception);
        }

        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return (RSAPublicKey) keyFactory.generatePublic(new X509EncodedKeySpec(keyBytes));
    }

    private static boolean containsAudience(Object audience, String requiredAudience) {
        if (audience instanceof String value) {
            return requiredAudience.equals(value);
        }
        if (audience instanceof Collection<?> values) {
            return values.stream().anyMatch(requiredAudience::equals);
        }
        return false;
    }

    private static boolean isPositiveNumericSubject(String subject) {
        if (subject == null || subject.isBlank()) {
            return false;
        }
        if (!subject.chars().allMatch(Character::isDigit)) {
            return false;
        }
        try {
            return Long.parseLong(subject) > 0;
        } catch (NumberFormatException exception) {
            return false;
        }
    }
}
