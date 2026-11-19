package com.github.nhordiienko23.userservice.service;

import com.github.nhordiienko23.userservice.dto.*;

public interface AdminUserService {
    UserResponse updateUser(Long adminId,  Long targetUserId, UpdateUserRequest request);

    void deleteUser(Long adminId, Long targetUserId);

    void setUserPassword(Long adminId,  Long targetUserId, AdminSetNewPasswordRequest request);

    UserResponse createUser(Long adminId, AdminCreateUserRequest request);

    UserResponse updateUserRole(Long adminId,  Long userId, UpdateUserRoleRequest request);
}
