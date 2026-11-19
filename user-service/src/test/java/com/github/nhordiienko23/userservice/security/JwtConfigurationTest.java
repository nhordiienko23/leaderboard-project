
package com.github.nhordiienko23.userservice.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JWT configuration tests")
class JwtConfigurationTest {

    private static final String KEY_ID = "user-service-rsa-1";
    private static final String ISSUER = "http://user-service";

    private final JwtConfiguration sut = new JwtConfiguration();

    @Nested
    @DisplayName("JWT encoder")
    class JwtEncoderTests {

        @Test
        @DisplayName("Should create a verifiable JWT when given a valid PKCS8 RSA key")
        void jwtEncoderShouldCreateVerifiableJwtWhenGivenValidPkcs8RsaKey()
                throws Exception {
            KeyPair keyPair = generateRsaKeyPair();
            Resource privateKeyResource = pemResource(keyPair);

            var encoder = sut.jwtEncoder(privateKeyResource);

            Instant issuedAt = Instant.now()
                    .truncatedTo(ChronoUnit.SECONDS)
                    .minusSeconds(1);
            Instant expiresAt = issuedAt.plusSeconds(120);

            var encodedJwt = encoder.encode(JwtEncoderParameters.from(
                    JwsHeader.with(SignatureAlgorithm.RS256)
                            .keyId(KEY_ID)
                            .build(),
                    JwtClaimsSet.builder()
                            .issuer(ISSUER)
                            .subject("user-42")
                            .audience(List.of("leaderboard"))
                            .issuedAt(issuedAt)
                            .expiresAt(expiresAt)
                            .build()
            ));

            JwtDecoder decoder = NimbusJwtDecoder
                    .withPublicKey((RSAPublicKey) keyPair.getPublic())
                    .build();

            var decodedJwt = decoder.decode(encodedJwt.getTokenValue());

            assertThat(decodedJwt.getHeaders())
                    .containsEntry("kid", KEY_ID)
                    .containsEntry("alg", "RS256");
            assertThat(decodedJwt.getIssuer()).hasToString(ISSUER);
            assertThat(decodedJwt.getSubject()).isEqualTo("user-42");
            assertThat(decodedJwt.getAudience()).containsExactly("leaderboard");
            assertThat(decodedJwt.getIssuedAt()).isEqualTo(issuedAt);
            assertThat(decodedJwt.getExpiresAt()).isEqualTo(expiresAt);
        }

        @Test
        @DisplayName("Should propagate the I/O exception when the key resource cannot be read")
        void jwtEncoderShouldPropagateIoExceptionWhenKeyResourceCannotBeRead() {
            Resource unreadableResource = new ByteArrayResource(new byte[0]) {
                @Override
                public java.io.InputStream getInputStream() throws IOException {
                    throw new IOException("key file unavailable");
                }
            };

            assertThatThrownBy(() -> sut.jwtEncoder(unreadableResource))
                    .isInstanceOf(IOException.class)
                    .hasMessage("key file unavailable");
        }

        @Test
        @DisplayName("Should reject invalid Base64 when the PEM body is malformed")
        void jwtEncoderShouldRejectInvalidBase64WhenPemBodyIsMalformed() {
            Resource malformedResource = new ByteArrayResource(
                    """
                    -----BEGIN PRIVATE KEY-----
                    !!!!
                    -----END PRIVATE KEY-----
                    """.getBytes(StandardCharsets.UTF_8)
            );

            assertThatThrownBy(() -> sut.jwtEncoder(malformedResource))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Illegal base64 character 21");
        }

        @Test
        @DisplayName("Should fail when the key resource is null")
        void jwtEncoderShouldFailWhenKeyResourceIsNull() {
            assertThatThrownBy(() -> sut.jwtEncoder(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("privateKeyResource");
        }
    }

    private static KeyPair generateRsaKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static Resource pemResource(KeyPair keyPair) {
        String encodedKey = Base64.getMimeEncoder(
                        64,
                        "\n".getBytes(StandardCharsets.US_ASCII)
                )
                .encodeToString(keyPair.getPrivate().getEncoded());

        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + encodedKey
                + "\n-----END PRIVATE KEY-----";

        return new ByteArrayResource(pem.getBytes(StandardCharsets.UTF_8));
    }
}
