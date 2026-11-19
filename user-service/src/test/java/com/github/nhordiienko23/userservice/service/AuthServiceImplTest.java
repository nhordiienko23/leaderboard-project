package com.github.nhordiienko23.userservice.service;

import com.github.nhordiienko23.userservice.dto.LoginRequest;
import com.github.nhordiienko23.userservice.dto.RefreshTokenRequest;
import com.github.nhordiienko23.userservice.dto.RegisterRequest;
import com.github.nhordiienko23.userservice.dto.TokenResponse;
import com.github.nhordiienko23.userservice.dto.UserResponse;
import com.github.nhordiienko23.userservice.entity.User;
import com.github.nhordiienko23.userservice.entity.UserRole;
import com.github.nhordiienko23.userservice.exception.InvalidAuthenticationException;
import com.github.nhordiienko23.userservice.exception.UserNotFoundException;
import com.github.nhordiienko23.userservice.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Auth service tests")
class AuthServiceImplTest {

    private static final Long USER_ID = 2L;
    private static final String PASSWORD = "correct-password";
    private static final String ACCESS_TOKEN = "access-token";
    private static final String REFRESH_TOKEN = "refresh-token";
    private static final long ACCESS_TOKEN_TTL_SECONDS = 900L;

    @Mock
    private UserService userService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private RefreshTokenService refreshTokenService;

    private AuthServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AuthServiceImpl(
                userService, passwordEncoder, jwtService, refreshTokenService
        );
    }

    @Test
    @DisplayName("Should register a new user with the USER role")
    void registerCreatesUserWithUserRole() {
        RegisterRequest request = RegisterRequest.builder()
                .username("new-user")
                .email("new@example.com")
                .password(PASSWORD)
                .build();

        UserResponse response = response(USER_ID, UserRole.USER);

        when(userService.createUser(request, UserRole.USER)).thenReturn(response);

        UserResponse result = service.register(request);

        assertSame(response, result);

        verify(userService).createUser(request, UserRole.USER);
        verifyNoInteractions(passwordEncoder, jwtService, refreshTokenService);
    }

    @Test
    @DisplayName("Should trim the login identifier and return tokens for valid credentials")
    void loginTrimsIdentifierAndIssuesTokensForValidCredentials() {
        User user = user(USER_ID, UserRole.USER);

        when(userService.findUserByUsernameOrEmail("user@example.com"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, user.getPassword())).thenReturn(true);
        when(jwtService.createAccessToken(user)).thenReturn(ACCESS_TOKEN);
        when(refreshTokenService.create(USER_ID)).thenReturn(REFRESH_TOKEN);
        when(jwtService.getAccessTokenTtlSeconds())
                .thenReturn(ACCESS_TOKEN_TTL_SECONDS);

        TokenResponse result = service.login(
                new LoginRequest("  user@example.com  ", PASSWORD)
        );

        assertEquals(
                new TokenResponse(
                        ACCESS_TOKEN, REFRESH_TOKEN, "Bearer", ACCESS_TOKEN_TTL_SECONDS
                ),
                result
        );

        verify(userService).findUserByUsernameOrEmail("user@example.com");
        verify(passwordEncoder).matches(PASSWORD, user.getPassword());
        verify(jwtService).createAccessToken(user);
        verify(refreshTokenService).create(USER_ID);
    }

    @Test
    @DisplayName("Should accept a password containing exactly 72 UTF-8 bytes")
    void loginAcceptsPasswordAt72Utf8Bytes() {
        String password = "界".repeat(24);
        User user = user(USER_ID, UserRole.USER);

        when(userService.findUserByUsernameOrEmail("user"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches(password, user.getPassword())).thenReturn(true);
        when(jwtService.createAccessToken(user)).thenReturn(ACCESS_TOKEN);
        when(refreshTokenService.create(USER_ID)).thenReturn(REFRESH_TOKEN);
        when(jwtService.getAccessTokenTtlSeconds())
                .thenReturn(ACCESS_TOKEN_TTL_SECONDS);

        TokenResponse result = service.login(new LoginRequest("user", password));

        assertEquals(ACCESS_TOKEN, result.accessToken());
        assertEquals(REFRESH_TOKEN, result.refreshToken());

        verify(passwordEncoder).matches(password, user.getPassword());
    }

    @Test
    @DisplayName("Should reject passwords exceeding 72 UTF-8 bytes before user lookup")
    void loginRejectsPasswordOver72Utf8BytesWithoutLookingUpUser() {
        String password = "界".repeat(25);

        assertThrows(
                InvalidAuthenticationException.class,
                () -> service.login(new LoginRequest("user", password))
        );

        verifyNoInteractions(
                userService, passwordEncoder, jwtService, refreshTokenService
        );
    }

    @Test
    @DisplayName("Should reject login when no matching user exists")
    void loginRejectsUnknownUser() {
        when(userService.findUserByUsernameOrEmail("unknown"))
                .thenReturn(Optional.empty());

        assertThrows(
                InvalidAuthenticationException.class,
                () -> service.login(new LoginRequest("unknown", PASSWORD))
        );

        verify(userService).findUserByUsernameOrEmail("unknown");
        verifyNoInteractions(passwordEncoder, jwtService, refreshTokenService);
    }

    @Test
    @DisplayName("Should reject login when the password does not match")
    void loginRejectsIncorrectPassword() {
        User user = user(USER_ID, UserRole.USER);

        when(userService.findUserByUsernameOrEmail("user"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, user.getPassword())).thenReturn(false);

        assertThrows(
                InvalidAuthenticationException.class,
                () -> service.login(new LoginRequest("user", PASSWORD))
        );

        verify(passwordEncoder).matches(PASSWORD, user.getPassword());
        verifyNoInteractions(jwtService, refreshTokenService);
    }

    @Test
    @DisplayName("Should rotate the refresh token and issue an access token")
    void refreshRotatesTokenAndIssuesAccessToken() {
        String oldRefreshToken = "old-refresh-token";
        User user = user(USER_ID, UserRole.ADMIN);

        when(refreshTokenService.rotate(oldRefreshToken))
                .thenReturn(new RefreshTokenService.RotatedRefreshToken(
                        USER_ID, REFRESH_TOKEN
                ));
        when(userService.findUserById(USER_ID)).thenReturn(user);
        when(jwtService.createAccessToken(user)).thenReturn(ACCESS_TOKEN);
        when(jwtService.getAccessTokenTtlSeconds())
                .thenReturn(ACCESS_TOKEN_TTL_SECONDS);

        TokenResponse result = service.refresh(
                new RefreshTokenRequest(oldRefreshToken)
        );

        assertEquals(
                new TokenResponse(
                        ACCESS_TOKEN, REFRESH_TOKEN, "Bearer", ACCESS_TOKEN_TTL_SECONDS
                ),
                result
        );

        verify(refreshTokenService).rotate(oldRefreshToken);
        verify(userService).findUserById(USER_ID);
        verify(jwtService).createAccessToken(user);
        verify(refreshTokenService, never()).create(USER_ID);
    }

    @Test
    @DisplayName("Should reject refresh when the rotated token refers to a missing user")
    void refreshRejectsMissingUser() {
        String oldRefreshToken = "old-refresh-token";

        when(refreshTokenService.rotate(oldRefreshToken))
                .thenReturn(new RefreshTokenService.RotatedRefreshToken(
                        USER_ID, REFRESH_TOKEN
                ));
        when(userService.findUserById(USER_ID))
                .thenThrow(new UserNotFoundException("User not found"));

        assertThrows(
                InvalidAuthenticationException.class,
                () -> service.refresh(new RefreshTokenRequest(oldRefreshToken))
        );

        verify(refreshTokenService).rotate(oldRefreshToken);
        verify(userService).findUserById(USER_ID);
        verifyNoInteractions(jwtService);
    }

    @Test
    @DisplayName("Should propagate refresh-token rotation failures")
    void refreshPropagatesRotationFailure() {
        String oldRefreshToken = "invalid-refresh-token";

        InvalidAuthenticationException failure =
                new InvalidAuthenticationException(
                        "Invalid or expired refresh token"
                );

        when(refreshTokenService.rotate(oldRefreshToken)).thenThrow(failure);

        InvalidAuthenticationException thrown = assertThrows(
                InvalidAuthenticationException.class,
                () -> service.refresh(new RefreshTokenRequest(oldRefreshToken))
        );

        assertSame(failure, thrown);

        verify(refreshTokenService).rotate(oldRefreshToken);
        verifyNoInteractions(userService, jwtService);
    }

    @Test
    @DisplayName("Should revoke the refresh token on logout")
    void logoutRevokesRefreshToken() {
        String refreshToken = "refresh-token";

        service.logout(new RefreshTokenRequest(refreshToken));

        verify(refreshTokenService).revoke(refreshToken);
        verifyNoInteractions(userService, passwordEncoder, jwtService);
    }

    private static User user(Long id, UserRole role) {
        return User.builder()
                .id(id)
                .username("user")
                .email("user@example.com")
                .password("encoded-password")
                .role(role)
                .build();
    }

    private static UserResponse response(Long id, UserRole role) {
        return new UserResponse(id, "user", "user@example.com", role, null, null, null);
    }
}
