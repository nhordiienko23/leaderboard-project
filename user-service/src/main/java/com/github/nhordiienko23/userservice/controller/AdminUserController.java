package com.github.nhordiienko23.userservice.controller;

import com.github.nhordiienko23.userservice.dto.*;
import com.github.nhordiienko23.userservice.service.AdminUserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@Tag(name = "Admin - Users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    @PostMapping()
    public ResponseEntity<UserResponse> createUser(
            @RequestHeader(value = "X-User-Id", required = false) Long adminId,
            @Valid @RequestBody AdminCreateUserRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(adminUserService.createUser(adminId, request));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<UserResponse> updateUserProfile(
            @RequestHeader(value = "X-User-Id", required = false) Long adminId,
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(adminUserService.updateUser(adminId, id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(
            @RequestHeader(value = "X-User-Id", required = false) Long adminId,
            @PathVariable Long id) {
        adminUserService.deleteUser(adminId, id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/password")
    public ResponseEntity<Void> setUserPassword(
            @RequestHeader(value = "X-User-Id", required = false) Long adminId,
            @PathVariable Long id,
            @Valid @RequestBody AdminSetNewPasswordRequest request) {
        adminUserService.setUserPassword(adminId, id, request);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/role")
    public ResponseEntity<UserResponse> updateUserRole(
            @RequestHeader(value = "X-User-Id", required = false) Long adminId,
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRoleRequest request) {

        return ResponseEntity.ok(
                adminUserService.updateUserRole(
                        adminId,
                        id,
                        request
                )
        );
    }
}
