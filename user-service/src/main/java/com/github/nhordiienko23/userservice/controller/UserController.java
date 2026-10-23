package com.github.nhordiienko23.userservice.controller;

import com.github.nhordiienko23.userservice.dto.PageResponse;
import com.github.nhordiienko23.userservice.dto.UpdatePasswordRequest;
import com.github.nhordiienko23.userservice.dto.UpdateUserRequest;
import com.github.nhordiienko23.userservice.dto.UserResponse;
import com.github.nhordiienko23.userservice.security.UserAccessPolicy;
import com.github.nhordiienko23.userservice.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Validated
@Tag(name = "Users")
public class UserController {

    private final UserService userService;
    private final UserAccessPolicy accessPolicy;

    /** Any authenticated user may view another user's public profile. */
    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUserById(
            @RequestHeader(value = "X-User-Id", required = false) Long currentUserId,
            @PathVariable Long id) {
        accessPolicy.requireAuthenticated(currentUserId);
        return ResponseEntity.ok(userService.getUserById(id));
    }

    /** Any authenticated user may browse the paginated user directory. */
    @GetMapping
    public ResponseEntity<PageResponse<UserResponse>> getUsers(
            @RequestHeader(value = "X-User-Id", required = false) Long currentUserId,
            @ParameterObject Pageable pageable) {
        accessPolicy.requireAuthenticated(currentUserId);
        Page<UserResponse> page = userService.getUsers(pageable);
        return ResponseEntity.ok(PageResponse.from(page));
    }

    /** The user ID comes only from the trusted Gateway header, never from the URL/body. */
    @PatchMapping("/me")
    public ResponseEntity<UserResponse> updateMyProfile(
            @RequestHeader(value = "X-User-Id", required = false) Long currentUserId,
            @Valid @RequestBody UpdateUserRequest request) {
        Long authenticatedUserId = accessPolicy.requireAuthenticated(currentUserId);
        return ResponseEntity.ok(userService.updateUser(authenticatedUserId, request));
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteMyAccount(
            @RequestHeader(value = "X-User-Id", required = false) Long currentUserId) {
        Long authenticatedUserId = accessPolicy.requireAuthenticated(currentUserId);
        userService.deleteUser(authenticatedUserId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/me/password")
    public ResponseEntity<Void> changeMyPassword(
            @RequestHeader(value = "X-User-Id", required = false) Long currentUserId,
            @Valid @RequestBody UpdatePasswordRequest request) {
        Long authenticatedUserId = accessPolicy.requireAuthenticated(currentUserId);
        userService.changePassword(authenticatedUserId, request);
        return ResponseEntity.ok().build();
    }
}
