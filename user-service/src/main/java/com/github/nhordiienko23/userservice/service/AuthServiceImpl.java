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

import lombok.RequiredArgsConstructor;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    @Override
    @Transactional
    public UserResponse register(RegisterRequest request) {
        // Public registration can create only USER accounts.
        return userService.createUser(request, UserRole.USER);
    }

    @Override
    @Transactional
    public TokenResponse login(LoginRequest request) {
        String identifier = request.login().trim();
        String password = request.password();

        // Do not let an oversized UTF-8 password cause a BCrypt error.
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new InvalidAuthenticationException(
                    "Invalid username/email or password"
            );
        }

        User user = userService.findUserByUsernameOrEmail(identifier)
                .filter(found ->
                        passwordEncoder.matches(
                                password,
                                found.getPassword()
                        )
                )
                .orElseThrow(() -> new InvalidAuthenticationException(
                        "Invalid username/email or password"
                ));

        return issueTokens(user);
    }

    @Override
    @Transactional
    public TokenResponse refresh(RefreshTokenRequest request) {
        RotatedRefreshToken rotated =
                refreshTokenService.rotate(request.refreshToken());

        User user;

        try {
            user = userService.findUserById(rotated.userId());
        } catch (UserNotFoundException exception) {
            throw new InvalidAuthenticationException(
                    "Invalid refresh token"
            );
        }

        // The role comes from the current User record, not the old JWT.
        return new TokenResponse(
                jwtService.createAccessToken(user),
                rotated.refreshToken(),
                "Bearer",
                jwtService.getAccessTokenTtlSeconds()
        );
    }

    @Override
    @Transactional
    public void logout(RefreshTokenRequest request) {
        refreshTokenService.revoke(request.refreshToken());
    }

    private TokenResponse issueTokens(User user) {
        String accessToken = jwtService.createAccessToken(user);
        String refreshToken = refreshTokenService.create(user.getId());

        return new TokenResponse(
                accessToken,
                refreshToken,
                "Bearer",
                jwtService.getAccessTokenTtlSeconds()
        );
    }
}