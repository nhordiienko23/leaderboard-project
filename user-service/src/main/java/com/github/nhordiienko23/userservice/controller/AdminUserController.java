
package com.github.nhordiienko23.userservice.controller;

import com.github.nhordiienko23.userservice.dto.AdminCreateUserRequest;
import com.github.nhordiienko23.userservice.dto.AdminSetNewPasswordRequest;
import com.github.nhordiienko23.userservice.dto.UpdateUserRequest;
import com.github.nhordiienko23.userservice.dto.UpdateUserRoleRequest;
import com.github.nhordiienko23.userservice.dto.UserResponse;
import com.github.nhordiienko23.userservice.service.AdminUserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@Tag(name = "Admin - Users")
public class AdminUserController {

    private static final String USER_ID_HEADER = "X-User-Id";

    private final AdminUserService adminUserService;

    @PostMapping
    public ResponseEntity<UserResponse> createUser(
            @RequestHeader(value = USER_ID_HEADER, required = false) Long adminId,
            @Valid @RequestBody AdminCreateUserRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(adminUserService.createUser(adminId, request));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<UserResponse> updateUserProfile(
            @RequestHeader(value = USER_ID_HEADER, required = false) Long adminId,
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRequest request
    ) {
        return ResponseEntity.ok(adminUserService.updateUser(adminId, id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(
            @RequestHeader(value = USER_ID_HEADER, required = false) Long adminId,
            @PathVariable Long id
    ) {
        adminUserService.deleteUser(adminId, id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/password")
    public ResponseEntity<Void> setUserPassword(
            @RequestHeader(value = USER_ID_HEADER, required = false) Long adminId,
            @PathVariable Long id,
            @Valid @RequestBody AdminSetNewPasswordRequest request
    ) {
        adminUserService.setUserPassword(adminId, id, request);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/role")
    public ResponseEntity<UserResponse> updateUserRole(
            @RequestHeader(value = USER_ID_HEADER, required = false) Long adminId,
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRoleRequest request
    ) {
        return ResponseEntity.ok(
                adminUserService.updateUserRole(adminId, id, request)
        );
    }
}
