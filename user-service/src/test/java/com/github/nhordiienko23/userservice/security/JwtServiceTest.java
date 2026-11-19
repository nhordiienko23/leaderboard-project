package com.github.nhordiienko23.userservice.security;

import com.github.nhordiienko23.userservice.entity.User;
import com.github.nhordiienko23.userservice.entity.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("JWT service tests")
class JwtServiceTest {

    private static final String ISSUER = "http://user-service";
    private static final String AUDIENCE = "leaderboard";

    private final JwtEncoder jwtEncoder = mock(JwtEncoder.class);
    private final Jwt encodedJwt = mock(Jwt.class);
    private final JwtService sut = new JwtService(jwtEncoder);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(sut, "issuer", ISSUER);
        ReflectionTestUtils.setField(sut, "audience", AUDIENCE);
        ReflectionTestUtils.setField(sut, "accessTokenTtlSeconds", 900L);
        when(encodedJwt.getTokenValue()).thenReturn("encoded-token");
    }

    @Nested
    @DisplayName("createAccessToken")
    class CreateAccessToken {

        @Test
        @DisplayName("createAccessToken: should encode expected claims and header for a valid user")
        void createAccessToken_shouldEncodeExpectedClaimsAndHeader_whenUserIsValid() {
            User user = User.builder().id(42L).role(UserRole.ADMIN).build();
            ArgumentCaptor<JwtEncoderParameters> parametersCaptor =
                    ArgumentCaptor.forClass(JwtEncoderParameters.class);
            Instant before = Instant.now();

            when(jwtEncoder.encode(any(JwtEncoderParameters.class))).thenReturn(encodedJwt);

            String token = sut.createAccessToken(user);

            Instant after = Instant.now();
            verify(jwtEncoder).encode(parametersCaptor.capture());
            JwtEncoderParameters parameters = parametersCaptor.getValue();
            JwtClaimsSet claims = parameters.getClaims();

            assertThat(token).isEqualTo("encoded-token");
            assertThat(claims.getIssuer()).hasToString(ISSUER);
            assertThat(claims.getSubject()).isEqualTo("42");
            assertThat(claims.getAudience()).containsExactly(AUDIENCE);
            assertThat(claims.getIssuedAt()).isBetween(before, after);
            assertThat(claims.getExpiresAt())
                    .isEqualTo(claims.getIssuedAt().plusSeconds(900));
            assertThat(UUID.fromString(claims.getId())).isNotNull();
            assertThat(claims.getClaims()).containsEntry("role", "ADMIN");
            assertThat(parameters.getJwsHeader().getAlgorithm())
                    .isEqualTo(SignatureAlgorithm.RS256);
            assertThat(parameters.getJwsHeader().getKeyId())
                    .isEqualTo("user-service-rsa-1");

            verify(encodedJwt).getTokenValue();
            verifyNoMoreInteractions(jwtEncoder, encodedJwt);
        }

        @Test
        @DisplayName("createAccessToken: should propagate encoder failures")
        void createAccessToken_shouldPropagateEncoderFailure_whenEncodingFails() {
            User user = User.builder().id(42L).role(UserRole.USER).build();
            IllegalStateException failure = new IllegalStateException("signing failed");
            when(jwtEncoder.encode(any(JwtEncoderParameters.class))).thenThrow(failure);

            assertThatThrownBy(() -> sut.createAccessToken(user))
                    .isSameAs(failure)
                    .hasMessage("signing failed");

            verify(jwtEncoder).encode(any(JwtEncoderParameters.class));
            verifyNoMoreInteractions(jwtEncoder, encodedJwt);
        }

        @Test
        @DisplayName("createAccessToken: should fail without encoding when user is null")
        void createAccessToken_shouldFailWithoutEncoding_whenUserIsNull() {
            assertThatThrownBy(() -> sut.createAccessToken(null))
                    .isInstanceOf(NullPointerException.class);

            verifyNoInteractions(jwtEncoder, encodedJwt);
        }

        @Test
        @DisplayName("createAccessToken: should fail without encoding when user ID is null")
        void createAccessToken_shouldFailWithoutEncoding_whenUserIdIsNull() {
            User user = User.builder().role(UserRole.USER).build();

            assertThatThrownBy(() -> sut.createAccessToken(user))
                    .isInstanceOf(NullPointerException.class);

            verifyNoInteractions(jwtEncoder, encodedJwt);
        }

        @Test
        @DisplayName("createAccessToken: should fail without encoding when user role is null")
        void createAccessToken_shouldFailWithoutEncoding_whenUserRoleIsNull() {
            User user = User.builder().id(42L).role(null).build();

            assertThatThrownBy(() -> sut.createAccessToken(user))
                    .isInstanceOf(NullPointerException.class);

            verifyNoInteractions(jwtEncoder, encodedJwt);
        }
    }

    @Nested
    @DisplayName("getAccessTokenTtlSeconds")
    class GetAccessTokenTtlSeconds {

        @Test
        @DisplayName("getAccessTokenTtlSeconds: should return the configured lifetime")
        void getAccessTokenTtlSeconds_shouldReturnConfiguredLifetime_whenCalled() {
            ReflectionTestUtils.setField(sut, "accessTokenTtlSeconds", 120L);

            assertThat(sut.getAccessTokenTtlSeconds()).isEqualTo(120L);
            verifyNoInteractions(jwtEncoder, encodedJwt);
        }
    }
}