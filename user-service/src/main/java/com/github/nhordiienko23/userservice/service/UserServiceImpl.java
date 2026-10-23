package com.github.nhordiienko23.userservice.service;

import com.github.nhordiienko23.userservice.dto.RegisterRequest;
import com.github.nhordiienko23.userservice.dto.UpdatePasswordRequest;
import com.github.nhordiienko23.userservice.dto.UpdateUserRequest;
import com.github.nhordiienko23.userservice.dto.UserResponse;
import com.github.nhordiienko23.userservice.entity.User;
import com.github.nhordiienko23.userservice.entity.UserRole;
import com.github.nhordiienko23.userservice.exception.InvalidUserArgumentException;
import com.github.nhordiienko23.userservice.exception.UserNotFoundException;
import com.github.nhordiienko23.userservice.mapper.UserMapper;
import com.github.nhordiienko23.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public UserResponse createUser(RegisterRequest request, UserRole userRole) {
        if (userRole == null) {
            throw new InvalidUserArgumentException("User role is required");
        }
        String username = request.username().trim();
        String email = request.email().trim();
        String password = request.password();
        userRepository.validateUsername(username);
        userRepository.validateEmail(email);
        validateBcryptPasswordLength(password);
        RegisterRequest normalizedRequest = RegisterRequest.builder()
                .username(username)
                .email(email)
                .password(password)
                .build();

        User user = userMapper.toEntity(normalizedRequest);

        user.setPassword(passwordEncoder.encode(password));
        user.setRole(userRole);

        User savedUser = userRepository.save(user);

        log.info(
                "New user was created id={}, login={}",
                savedUser.getId(),
                savedUser.getUsername()
        );

        return userMapper.toResponse(savedUser);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserById(Long userId) {
        return userMapper.toResponse(findUserById(userId));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserResponse> getUsers(Pageable pageable) {
        return userRepository.findAll(pageable).map(userMapper::toResponse);
    }

    @Override
    @Transactional
    public UserResponse updateUser(Long userId, UpdateUserRequest request) {
        User user = findUserById(userId);

        if (request.username() != null && !request.username().isBlank()) {
            String username = request.username().trim();
            if (!username.equals(user.getUsername())) {
                userRepository.validateUsernameForUpdate(username, userId);
                user.setUsername(username);
            }
        }

        if (request.email() != null && !request.email().isBlank()) {
            String email = request.email().trim();
            if (!email.equalsIgnoreCase(user.getEmail())) {
                userRepository.validateEmailForUpdate(email, userId);
                user.setEmail(email);
            }
        }

        User saved = userRepository.save(user);
        log.info("User profile updated: id={}", saved.getId());
        return userMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void deleteUser(Long userId) {
        User user = findUserById(userId);
        userRepository.delete(user);
        log.info("User deleted: id={}", userId);
    }

    @Override
    @Transactional
    public void changePassword(Long userId, UpdatePasswordRequest request) {
        User user = findUserById(userId);

        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new InvalidUserArgumentException("Current password is incorrect");
        }
        if (request.currentPassword().equals(request.newPassword())) {
            throw new InvalidUserArgumentException("New password must be different from current password");
        }
        validateBcryptPasswordLength(request.newPassword());

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        log.info("User password changed: id={}", userId);
    }

    @Override
    @Transactional(readOnly = true)
    public User findUserById(Long userId) {
        if (userId == null || userId <= 0) {
            throw new InvalidUserArgumentException("User ID must be positive");
        }
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
    }


    @Override
    @Transactional(readOnly = true)
    public Optional<User> findUserByUsernameOrEmail(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return Optional.empty();
        }

        String value = identifier.trim();

        return userRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase(
                value,
                value
        );
    }


    /**
     * BCrypt only considers up to 72 bytes; characters and bytes are not equivalent.
     */
    private void validateBcryptPasswordLength(String password) {
        if (password == null || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new InvalidUserArgumentException("Password must not exceed 72 UTF-8 bytes");
        }
    }

}