package com.github.nhordiienko23.userservice.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;

@Configuration
public class JwtConfiguration {

    private static final String KEY_ID = "user-service-rsa-1";

    @Bean
    public JwtEncoder jwtEncoder(
            @Value("${app.security.jwt.private-key-location}")
            Resource privateKeyResource
    ) throws Exception {

        String pem;

        try (InputStream input = privateKeyResource.getInputStream()) {
            pem = new String(
                    input.readAllBytes(),
                    StandardCharsets.UTF_8
            );
        }

        String encodedKey = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");

        byte[] keyBytes = Base64.getDecoder().decode(encodedKey);

        KeyFactory keyFactory = KeyFactory.getInstance("RSA");

        RSAPrivateCrtKey privateKey =
                (RSAPrivateCrtKey) keyFactory.generatePrivate(
                        new PKCS8EncodedKeySpec(keyBytes)
                );

        RSAPublicKey publicKey = (RSAPublicKey) keyFactory.generatePublic(
                new RSAPublicKeySpec(
                        privateKey.getModulus(),
                        privateKey.getPublicExponent()
                )
        );

        RSAKey rsaKey = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(KEY_ID)
                .build();

        JWKSource<SecurityContext> jwkSource =
                new ImmutableJWKSet<>(new JWKSet(rsaKey));

        return new NimbusJwtEncoder(jwkSource);
    }
}