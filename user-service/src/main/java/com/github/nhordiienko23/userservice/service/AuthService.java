package com.github.nhordiienko23.userservice.service;

import com.github.nhordiienko23.userservice.dto.LoginRequest;
import com.github.nhordiienko23.userservice.dto.RefreshTokenRequest;
import com.github.nhordiienko23.userservice.dto.RegisterRequest;
import com.github.nhordiienko23.userservice.dto.TokenResponse;
import com.github.nhordiienko23.userservice.dto.UserResponse;

public interface AuthService {

    UserResponse register(RegisterRequest request);

    TokenResponse login(LoginRequest request);

    TokenResponse refresh(RefreshTokenRequest request);

    void logout(RefreshTokenRequest request);
}