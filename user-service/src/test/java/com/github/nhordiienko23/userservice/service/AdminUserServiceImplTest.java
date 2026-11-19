package com.github.nhordiienko23.userservice.service;

import com.github.nhordiienko23.userservice.dto.AdminCreateUserRequest;
import com.github.nhordiienko23.userservice.dto.AdminSetNewPasswordRequest;
import com.github.nhordiienko23.userservice.dto.RegisterRequest;
import com.github.nhordiienko23.userservice.dto.UpdateUserRequest;
import com.github.nhordiienko23.userservice.dto.UpdateUserRoleRequest;
import com.github.nhordiienko23.userservice.dto.UserResponse;
import com.github.nhordiienko23.userservice.entity.User;
import com.github.nhordiienko23.userservice.entity.UserRole;
import com.github.nhordiienko23.userservice.exception.InvalidUserArgumentException;
import com.github.nhordiienko23.userservice.exception.UserNotFoundException;
import com.github.nhordiienko23.userservice.mapper.UserMapper;
import com.github.nhordiienko23.userservice.repository.UserRepository;
import com.github.nhordiienko23.userservice.security.UserAccessPolicy;
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
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Admin user service tests")
class AdminUserServiceImplTest {

    private static final Long ADMIN_ID = 1L;
    private static final Long USER_ID = 2L;

    @Mock
    private UserService userService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private UserAccessPolicy accessPolicy;
    @Mock
    private UserMapper userMapper;

    private AdminUserServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminUserServiceImpl(
                userService, userRepository, passwordEncoder, accessPolicy, userMapper
        );
    }

    @Test
    @DisplayName("Should require admin access and delegate user update")
    void updateUserRequiresAdminAndDelegatesToUserService() {
        UpdateUserRequest request = new UpdateUserRequest("new-name", null);
        UserResponse response = response(USER_ID, UserRole.USER);
        when(userService.updateUser(USER_ID, request)).thenReturn(response);

        UserResponse result = service.updateUser(ADMIN_ID, USER_ID, request);

        assertSame(response, result);
        verify(accessPolicy).requireAdmin(ADMIN_ID);
        verify(userService).updateUser(USER_ID, request);
    }

    @Test
    @DisplayName("Should require admin access and delegate user deletion")
    void deleteUserRequiresAdminAndDelegatesToUserService() {
        service.deleteUser(ADMIN_ID, USER_ID);

        verify(accessPolicy).requireAdmin(ADMIN_ID);
        verify(userService).deleteUser(USER_ID);
    }

    @Test
    @DisplayName("Should map registration request and create user with requested role")
    void createUserMapsRequestAndDelegatesWithRequestedRole() {
        AdminCreateUserRequest request = new AdminCreateUserRequest(
                "new-user", "new@example.com", "password", UserRole.ADMIN
        );
        RegisterRequest registerRequest = RegisterRequest.builder()
                .username("new-user")
                .email("new@example.com")
                .password("password")
                .build();
        UserResponse response = response(USER_ID, UserRole.ADMIN);

        when(userMapper.toRegisterRequest(request)).thenReturn(registerRequest);
        when(userService.createUser(registerRequest, UserRole.ADMIN)).thenReturn(response);

        UserResponse result = service.createUser(ADMIN_ID, request);

        assertSame(response, result);
        verify(accessPolicy).requireAdmin(ADMIN_ID);
        verify(userMapper).toRegisterRequest(request);
        verify(userService).createUser(registerRequest, UserRole.ADMIN);
    }

    @Test
    @DisplayName("Should encode and save password containing exactly 72 UTF-8 bytes")
    void setUserPasswordEncodesAndSavesPasswordAt72Utf8Bytes() {
        String newPassword = "界".repeat(24);
        User user = user(USER_ID, UserRole.USER);

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode(newPassword)).thenReturn("encoded-password");

        service.setUserPassword(
                ADMIN_ID, USER_ID, new AdminSetNewPasswordRequest(newPassword)
        );

        assertEquals("encoded-password", user.getPassword());
        verify(accessPolicy).requireAdmin(ADMIN_ID);
        verify(passwordEncoder).encode(newPassword);
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("Should reject passwords exceeding 72 UTF-8 bytes without accessing repository")
    void setUserPasswordRejectsMoreThan72Utf8BytesWithoutLookingUpUser() {
        String tooLongPassword = "界".repeat(25);

        assertThrows(InvalidUserArgumentException.class, () ->
                service.setUserPassword(
                        ADMIN_ID, USER_ID, new AdminSetNewPasswordRequest(tooLongPassword)
                )
        );

        verify(accessPolicy).requireAdmin(ADMIN_ID);
        verifyNoInteractions(userRepository, passwordEncoder);
    }

    @Test
    @DisplayName("Should throw exception when target user does not exist")
    void setUserPasswordThrowsWhenTargetUserDoesNotExist() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () ->
                service.setUserPassword(
                        ADMIN_ID, USER_ID, new AdminSetNewPasswordRequest("valid-password")
                )
        );

        verify(accessPolicy).requireAdmin(ADMIN_ID);
        verify(userRepository).findById(USER_ID);
        verifyNoInteractions(passwordEncoder);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Should reject null role without looking up user")
    void updateUserRoleRejectsNullRoleWithoutFindingUser() {
        assertThrows(InvalidUserArgumentException.class, () ->
                service.updateUserRole(ADMIN_ID, USER_ID, new UpdateUserRoleRequest(null))
        );

        verify(accessPolicy).requireAdmin(ADMIN_ID);
        verifyNoInteractions(userService, userRepository, userMapper);
    }

    @Test
    @DisplayName("Should return mapped user without saving when role remains unchanged")
    void updateUserRoleReturnsMappedUserWithoutSavingWhenRoleIsUnchanged() {
        User user = user(USER_ID, UserRole.USER);
        UserResponse response = response(USER_ID, UserRole.USER);

        when(userService.findUserById(USER_ID)).thenReturn(user);
        when(userMapper.toResponse(user)).thenReturn(response);

        UserResponse result = service.updateUserRole(
                ADMIN_ID, USER_ID, new UpdateUserRoleRequest(UserRole.USER)
        );

        assertSame(response, result);
        verify(accessPolicy).requireAdmin(ADMIN_ID);
        verify(userMapper).toResponse(user);
        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("Should update and save user when role changes")
    void updateUserRoleSavesAndMapsUserWhenRoleChanges() {
        User user = user(USER_ID, UserRole.USER);
        UserResponse response = response(USER_ID, UserRole.ADMIN);

        when(userService.findUserById(USER_ID)).thenReturn(user);
        when(userRepository.save(user)).thenReturn(user);
        when(userMapper.toResponse(user)).thenReturn(response);

        UserResponse result = service.updateUserRole(
                ADMIN_ID, USER_ID, new UpdateUserRoleRequest(UserRole.ADMIN)
        );

        assertEquals(UserRole.ADMIN, user.getRole());
        assertSame(response, result);

        verify(accessPolicy).requireAdmin(ADMIN_ID);
        verify(userRepository).save(user);
        verify(userMapper).toResponse(user);
    }

    private static User user(Long id, UserRole role) {
        return User.builder()
                .id(id)
                .username("user")
                .email("user@example.com")
                .password("old-password")
                .role(role)
                .build();
    }

    private static UserResponse response(Long id, UserRole role) {
        return new UserResponse(id, "user", "user@example.com", role, null, null, null);
    }
}