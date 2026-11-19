package com.github.nhordiienko23.userservice.service;

import com.github.nhordiienko23.userservice.dto.*;
import com.github.nhordiienko23.userservice.entity.User;
import com.github.nhordiienko23.userservice.entity.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface UserService {

    UserResponse createUser(RegisterRequest request, UserRole userRole);

    UserResponse updateUser(Long userId, UpdateUserRequest request);
    void deleteUser(Long userId);
    UserResponse getUserById(Long userId);
    Page<UserResponse>getUsers(Pageable pageable);
    void changePassword(Long userId, UpdatePasswordRequest request);
    User findUserById(Long userId);
    Optional<User> findUserByUsernameOrEmail(String identifier);

}