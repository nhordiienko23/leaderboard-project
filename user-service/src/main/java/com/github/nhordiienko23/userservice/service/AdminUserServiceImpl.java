package com.github.nhordiienko23.userservice.service;

import com.github.nhordiienko23.userservice.dto.*;
import com.github.nhordiienko23.userservice.entity.User;
import com.github.nhordiienko23.userservice.entity.UserRole;
import com.github.nhordiienko23.userservice.exception.InvalidUserArgumentException;
import com.github.nhordiienko23.userservice.exception.UserNotFoundException;
import com.github.nhordiienko23.userservice.mapper.UserMapper;
import com.github.nhordiienko23.userservice.repository.UserRepository;
import com.github.nhordiienko23.userservice.security.UserAccessPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminUserServiceImpl implements AdminUserService {

    private final UserService userService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserAccessPolicy accessPolicy;
    private final UserMapper userMapper;

    @Override
    @Transactional
    public UserResponse updateUser(Long adminId, Long targetUserId,
                                   UpdateUserRequest request) {
        accessPolicy.requireAdmin(adminId);
        UserResponse updated = userService.updateUser(targetUserId, request);
        log.info("Admin  adminId={} updated user profile for user  userId={}", adminId, targetUserId);
        return updated;
    }

    @Override
    @Transactional
    public void deleteUser(Long adminId, Long targetUserId) {
        accessPolicy.requireAdmin(adminId);
        userService.deleteUser(targetUserId);
        log.info("Admin  adminId={} deleted user  userId={}", adminId, targetUserId);
    }

    @Override
    @Transactional
    public void setUserPassword(Long adminId, Long targetUserId,
                                AdminSetNewPasswordRequest request) {
        accessPolicy.requireAdmin(adminId);
        if (request.newPassword().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new InvalidUserArgumentException("Password must not exceed 72 UTF-8 bytes");
        }

        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        log.info("Admin adminId={} reset password for user userId={}", adminId, targetUserId);
    }

    @Override
    public UserResponse createUser(Long adminId, AdminCreateUserRequest request) {
        accessPolicy.requireAdmin(adminId);
        RegisterRequest registerRequest = userMapper.toRegisterRequest(request);
        UserRole userRole = request.role();
        UserResponse createdUser = userService.createUser(registerRequest, request.role());
        log.info(
                "Admin  adminId={} created user userId={} with role={}",
                adminId,
                createdUser.id(),
                userRole
        );

        return createdUser;
    }

    @Override
    @Transactional
    public UserResponse updateUserRole(
            Long adminId,
            Long userId,
            UpdateUserRoleRequest request
    ) {
        accessPolicy.requireAdmin(adminId);

        if (request.role() == null) {
            throw new InvalidUserArgumentException("User role is required");
        }

        User user = userService.findUserById(userId);

        UserRole oldRole = user.getRole();
        UserRole newRole = request.role();

        if (oldRole == newRole) {
            return userMapper.toResponse(user);
        }

        user.setRole(newRole);

        User savedUser = userRepository.save(user);

        log.info(
                "Admin adminId={} changed role for userId={} from {} to {}",
                adminId,
                userId,
                oldRole,
                newRole
        );

        return userMapper.toResponse(savedUser);
    }


}
