package com.github.nhordiienko23.userservice.security;

import com.github.nhordiienko23.userservice.entity.User;
import com.github.nhordiienko23.userservice.entity.UserRole;
import com.github.nhordiienko23.userservice.exception.ForbiddenOperationException;
import com.github.nhordiienko23.userservice.exception.InvalidAuthenticationException;
import com.github.nhordiienko23.userservice.exception.UnauthenticatedException;
import com.github.nhordiienko23.userservice.exception.UserNotFoundException;
import com.github.nhordiienko23.userservice.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("User access policy tests")
class UserAccessPolicyTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private UserAccessPolicy sut;

    @Nested
    @DisplayName("requireAuthenticated")
    class RequireAuthenticated {

        @ParameterizedTest
        @NullSource
        @ValueSource(longs = {Long.MIN_VALUE, -1L, 0L})
        @DisplayName("requireAuthenticated: should throw when user ID is null or non-positive")
        void requireAuthenticated_shouldThrow_whenUserIdIsNullOrNonPositive(Long userId) {
            assertThatThrownBy(() -> sut.requireAuthenticated(userId))
                    .isInstanceOf(UnauthenticatedException.class)
                    .hasMessage("Authentication is required");

            verifyNoInteractions(userService);
        }

        @ParameterizedTest
        @ValueSource(longs = {1L, Long.MAX_VALUE})
        @DisplayName("requireAuthenticated: should return the ID when it is positive")
        void requireAuthenticated_shouldReturnId_whenUserIdIsPositive(Long userId) {
            assertThat(sut.requireAuthenticated(userId)).isEqualTo(userId);

            verifyNoInteractions(userService);
        }
    }

    @Nested
    @DisplayName("requireAdmin")
    class RequireAdmin {

        @ParameterizedTest
        @NullSource
        @ValueSource(longs = {Long.MIN_VALUE, -1L, 0L})
        @DisplayName("requireAdmin: should throw when admin ID is null or non-positive")
        void requireAdmin_shouldThrow_whenAdminIdIsNullOrNonPositive(Long adminId) {
            assertThatThrownBy(() -> sut.requireAdmin(adminId))
                    .isInstanceOf(UnauthenticatedException.class)
                    .hasMessage("Authentication is required");

            verifyNoInteractions(userService);
        }

        @Test
        @DisplayName("requireAdmin: should return when the user has the admin role")
        void requireAdmin_shouldReturn_whenUserIsAdmin() {
            when(userService.findUserById(7L))
                    .thenReturn(User.builder().id(7L).role(UserRole.ADMIN).build());

            sut.requireAdmin(7L);

            verify(userService).findUserById(7L);
            verifyNoMoreInteractions(userService);
        }

        @Test
        @DisplayName("requireAdmin: should throw when the user has a regular role")
        void requireAdmin_shouldThrow_whenUserIsNotAdmin() {
            when(userService.findUserById(7L))
                    .thenReturn(User.builder().id(7L).role(UserRole.USER).build());

            assertThatThrownBy(() -> sut.requireAdmin(7L))
                    .isInstanceOf(ForbiddenOperationException.class)
                    .hasMessage("Administrator role required");

            verify(userService).findUserById(7L);
            verifyNoMoreInteractions(userService);
        }

        @Test
        @DisplayName("requireAdmin: should throw when the user role is null")
        void requireAdmin_shouldThrow_whenUserRoleIsNull() {
            when(userService.findUserById(7L))
                    .thenReturn(User.builder().id(7L).role(null).build());

            assertThatThrownBy(() -> sut.requireAdmin(7L))
                    .isInstanceOf(ForbiddenOperationException.class)
                    .hasMessage("Administrator role required");

            verify(userService).findUserById(7L);
            verifyNoMoreInteractions(userService);
        }

        @Test
        @DisplayName("requireAdmin: should translate a missing user into invalid authentication")
        void requireAdmin_shouldThrowInvalidAuthentication_whenUserIsNotFound() {
            when(userService.findUserById(7L))
                    .thenThrow(new UserNotFoundException("User not found"));

            assertThatThrownBy(() -> sut.requireAdmin(7L))
                    .isInstanceOf(InvalidAuthenticationException.class)
                    .hasMessage("Authentication required");

            verify(userService).findUserById(7L);
            verifyNoMoreInteractions(userService);
        }

        @Test
        @DisplayName("requireAdmin: should propagate unexpected service failures")
        void requireAdmin_shouldPropagateServiceFailure_whenLookupFailsUnexpectedly() {
            IllegalStateException failure = new IllegalStateException("service unavailable");
            when(userService.findUserById(7L)).thenThrow(failure);

            assertThatThrownBy(() -> sut.requireAdmin(7L))
                    .isSameAs(failure)
                    .hasMessage("service unavailable");

            verify(userService).findUserById(7L);
            verifyNoMoreInteractions(userService);
        }
    }
}