package com.github.nhordiienko23.apigateway;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

final class TestJwtSupport {

    static final String ISSUER = "http://user-service";
    static final String AUDIENCE = "api-gateway";

    private static final KeyPair TEST_KEY_PAIR = generateKeyPair();
    private static final KeyPair OTHER_KEY_PAIR = generateKeyPair();
    private static final JwtEncoder TEST_ENCODER = encoder(TEST_KEY_PAIR);
    private static final JwtEncoder OTHER_ENCODER = encoder(OTHER_KEY_PAIR);
    private static final Path TEST_PUBLIC_KEY = writePublicKey(TEST_KEY_PAIR);

    private TestJwtSupport() {
    }

    static String publicKeyLocation() {
        return TEST_PUBLIC_KEY.toUri().toString();
    }

    static String accessToken(String role) {
        return accessToken(ISSUER, AUDIENCE, "42", role, Instant.now().minusSeconds(5),
                Instant.now().plusSeconds(300), null, TEST_ENCODER);
    }

    static String accessTokenWithoutExpiration() {
        return accessToken(ISSUER, AUDIENCE, "42", "USER", Instant.now().minusSeconds(5),
                null, null, TEST_ENCODER);
    }

    static String accessToken(String issuer, Object audience, String subject, String role,
                              Instant issuedAt, Instant expiresAt, Instant notBefore) {
        return accessToken(issuer, audience, subject, role, issuedAt, expiresAt, notBefore, TEST_ENCODER);
    }

    static String accessTokenWithInvalidSignature() {
        return accessToken(ISSUER, AUDIENCE, "42", "USER", Instant.now().minusSeconds(5),
                Instant.now().plusSeconds(300), Instant.now().minusSeconds(60), OTHER_ENCODER);
    }

    private static String accessToken(String issuer, Object audience, String subject, String role,
                                      Instant issuedAt, Instant expiresAt, Instant notBefore,
                                      JwtEncoder encoder) {
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(subject)
                .issuedAt(issuedAt)
                .claim("role", role);

        if (expiresAt != null) {
            claims.expiresAt(expiresAt);
        }
        if (notBefore != null) {
            claims.notBefore(notBefore);
        }

        if (audience instanceof String value) {
            claims.audience(List.of(value));
        } else if (audience instanceof List<?> values) {
            claims.audience(values.stream().map(Object::toString).toList());
        }

        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
    }

    private static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to generate dedicated test RSA key", exception);
        }
    }

    private static JwtEncoder encoder(KeyPair keyPair) {
        RSAKey rsaKey = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                .privateKey((RSAPrivateKey) keyPair.getPrivate())
                .build();
        ImmutableJWKSet<SecurityContext> jwkSet = new ImmutableJWKSet<>(new JWKSet(rsaKey));
        return new NimbusJwtEncoder(jwkSet);
    }

    private static Path writePublicKey(KeyPair keyPair) {
        try {
            String encoded = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(
                    keyPair.getPublic().getEncoded()
            );
            String pem = "-----BEGIN PUBLIC KEY-----\n" + encoded + "\n-----END PUBLIC KEY-----\n";
            Path file = Files.createTempFile("api-gateway-test-public-", ".pem");
            Files.writeString(file, pem, StandardCharsets.UTF_8);
            file.toFile().deleteOnExit();
            return file;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to write dedicated test RSA public key", exception);
        }
    }
}
