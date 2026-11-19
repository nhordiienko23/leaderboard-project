package com.github.nhordiienko23.userservice.service;

import com.github.nhordiienko23.leaderboardcontract.event.UserEventType;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserServiceImpl tests")
class UserServiceImplTest {

    private static final Long USER_ID = 42L;
    private static final String PASSWORD = "correct-password";
    private static final String ENCODED_PASSWORD = "encoded-password";

    @Mock
    private UserRepository userRepository;

    @Spy
    private UserMapper userMapper = Mappers.getMapper(UserMapper.class);

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UserEventOutboxService userEventOutboxService;

    @InjectMocks
    private UserServiceImpl sut;

    @Nested
    @DisplayName("createUser")
    class CreateUser {

        @Test
        @DisplayName("createUser: should trim fields, encode password, save user, and enqueue creation")
        void createUser_shouldCreateUser_whenRequestIsValid() {
            RegisterRequest request = request("  new-user  ", "  new@example.com  ", PASSWORD);
            when(passwordEncoder.encode(PASSWORD)).thenReturn(ENCODED_PASSWORD);
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
                User saved = invocation.getArgument(0);
                saved.setId(USER_ID);
                return saved;
            });

            UserResponse result = sut.createUser(request, UserRole.USER);

            assertThat(result)
                    .isEqualTo(new UserResponse(
                            USER_ID, "new-user", "new@example.com",
                            UserRole.USER, null, null, null
                    ));

            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).validateUsername("new-user");
            verify(userRepository).validateEmail("new@example.com");
            verify(passwordEncoder).encode(PASSWORD);
            verify(userRepository).save(userCaptor.capture());

            assertThat(userCaptor.getValue().getUsername()).isEqualTo("new-user");
            assertThat(userCaptor.getValue().getEmail()).isEqualTo("new@example.com");
            assertThat(userCaptor.getValue().getPassword()).isEqualTo(ENCODED_PASSWORD);
            assertThat(userCaptor.getValue().getRole()).isEqualTo(UserRole.USER);

            verify(userEventOutboxService)
                    .enqueue(USER_ID, "new-user", UserEventType.CREATED);
        }

        @Test
        @DisplayName("createUser: should accept a password of exactly 72 UTF-8 bytes")
        void createUser_shouldAcceptPasswordAt72Utf8Bytes_whenPasswordIsMultibyte() {
            String password = "界".repeat(24);
            when(passwordEncoder.encode(password)).thenReturn(ENCODED_PASSWORD);
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
                User saved = invocation.getArgument(0);
                saved.setId(USER_ID);
                return saved;
            });

            UserResponse result = sut.createUser(
                    request("user", "user@example.com", password), UserRole.USER
            );

            assertThat(result.id()).isEqualTo(USER_ID);
            verify(passwordEncoder).encode(password);
            verify(userRepository).save(any(User.class));
            verify(userEventOutboxService)
                    .enqueue(USER_ID, "user", UserEventType.CREATED);
        }

        @Test
        @DisplayName("createUser: should reject a password over 72 UTF-8 bytes before saving")
        void createUser_shouldRejectPasswordOver72Utf8Bytes_whenPasswordIsMultibyte() {
            String password = "界".repeat(25);

            assertThatThrownBy(() -> sut.createUser(
                    request("user", "user@example.com", password), UserRole.USER
            ))
                    .isInstanceOf(InvalidUserArgumentException.class)
                    .hasMessage("Password must not exceed 72 UTF-8 bytes");

            verify(userRepository).validateUsername("user");
            verify(userRepository).validateEmail("user@example.com");
            verify(userRepository, never()).save(any(User.class));
            verifyNoInteractions(passwordEncoder, userEventOutboxService);
        }

        @Test
        @DisplayName("createUser: should reject a null role before accessing dependencies")
        void createUser_shouldRejectNullRole_whenRoleIsMissing() {
            assertThatThrownBy(() -> sut.createUser(
                    request("user", "user@example.com", PASSWORD), null
            ))
                    .isInstanceOf(InvalidUserArgumentException.class)
                    .hasMessage("User role is required");

            verifyNoInteractions(
                    userRepository, userMapper, passwordEncoder, userEventOutboxService
            );
        }
    }

    @Nested
    @DisplayName("getUserById")
    class GetUserById {

        @Test
        @DisplayName("getUserById: should return mapped user when user exists")
        void getUserById_shouldReturnMappedUser_whenUserExists() {
            User user = user(USER_ID, "user", "user@example.com");
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

            UserResponse result = sut.getUserById(USER_ID);

            assertThat(result).isEqualTo(response(USER_ID, "user", "user@example.com"));
            verify(userRepository).findById(USER_ID);
        }

        @Test
        @DisplayName("getUserById: should throw when user does not exist")
        void getUserById_shouldThrow_whenUserDoesNotExist() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> sut.getUserById(USER_ID))
                    .isInstanceOf(UserNotFoundException.class)
                    .hasMessage("User not found");

            verify(userRepository).findById(USER_ID);
        }
    }

    @Nested
    @DisplayName("getUsers")
    class GetUsers {

        @Test
        @DisplayName("getUsers: should return mapped page and pass through pageable")
        void getUsers_shouldReturnMappedPage_whenUsersExist() {
            var pageable = PageRequest.of(0, 10);
            User user = user(USER_ID, "user", "user@example.com");
            when(userRepository.findAll(pageable))
                    .thenReturn(new PageImpl<>(List.of(user), pageable, 1));

            var result = sut.getUsers(pageable);

            assertThat(result.getContent())
                    .containsExactly(response(USER_ID, "user", "user@example.com"));
            assertThat(result.getTotalElements()).isEqualTo(1);
            verify(userRepository).findAll(pageable);
        }
    }

    @Nested
    @DisplayName("updateUser")
    class UpdateUser {

        @Test
        @DisplayName("updateUser: should update trimmed username and enqueue event when username changes")
        void updateUser_shouldUpdateUsernameAndEnqueueEvent_whenUsernameChanges() {
            User user = user(USER_ID, "old-name", "user@example.com");
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(userRepository.save(user)).thenReturn(user);

            UserResponse result = sut.updateUser(
                    USER_ID, new UpdateUserRequest("  new-name  ", null)
            );

            assertThat(user.getUsername()).isEqualTo("new-name");
            assertThat(result.username()).isEqualTo("new-name");
            verify(userRepository).validateUsernameForUpdate("new-name", USER_ID);
            verify(userRepository).save(user);
            verify(userEventOutboxService)
                    .enqueue(USER_ID, "new-name", UserEventType.UPDATED);
        }

        @Test
        @DisplayName("updateUser: should update email without event when username is unchanged")
        void updateUser_shouldUpdateEmailWithoutEvent_whenOnlyEmailChanges() {
            User user = user(USER_ID, "user", "user@example.com");
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(userRepository.save(user)).thenReturn(user);

            UserResponse result = sut.updateUser(
                    USER_ID, new UpdateUserRequest(null, "  new@example.com  ")
            );

            assertThat(user.getEmail()).isEqualTo("new@example.com");
            assertThat(result.email()).isEqualTo("new@example.com");
            verify(userRepository).validateEmailForUpdate("new@example.com", USER_ID);
            verify(userRepository).save(user);
            verifyNoInteractions(userEventOutboxService);
        }

        @Test
        @DisplayName("updateUser: should ignore blank fields and avoid event when nothing changes")
        void updateUser_shouldIgnoreBlankFields_whenRequestFieldsAreBlank() {
            User user = user(USER_ID, "user", "user@example.com");
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(userRepository.save(user)).thenReturn(user);

            UserResponse result = sut.updateUser(
                    USER_ID, new UpdateUserRequest("  ", "")
            );

            assertThat(result).isEqualTo(response(USER_ID, "user", "user@example.com"));
            verify(userRepository).save(user);
            verify(userRepository, never()).validateUsernameForUpdate(any(), any());
            verify(userRepository, never()).validateEmailForUpdate(any(), any());
            verifyNoInteractions(userEventOutboxService);
        }

        @Test
        @DisplayName("updateUser: should not validate email when it differs only by case")
        void updateUser_shouldIgnoreEmailCaseOnlyChange_whenEmailIsEquivalent() {
            User user = user(USER_ID, "user", "user@example.com");
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(userRepository.save(user)).thenReturn(user);

            sut.updateUser(USER_ID, new UpdateUserRequest(null, "USER@EXAMPLE.COM"));

            verify(userRepository, never()).validateEmailForUpdate(any(), any());
            verify(userRepository).save(user);
            verifyNoInteractions(userEventOutboxService);
        }
    }

    @Nested
    @DisplayName("deleteUser")
    class DeleteUser {

        @Test
        @DisplayName("deleteUser: should delete user when user exists")
        void deleteUser_shouldDeleteUser_whenUserExists() {
            User user = user(USER_ID, "user", "user@example.com");
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

            sut.deleteUser(USER_ID);

            verify(userRepository).findById(USER_ID);
            verify(userRepository).delete(user);
        }

        @Test
        @DisplayName("deleteUser: should not delete when user does not exist")
        void deleteUser_shouldNotDelete_whenUserDoesNotExist() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> sut.deleteUser(USER_ID))
                    .isInstanceOf(UserNotFoundException.class)
                    .hasMessage("User not found");

            verify(userRepository).findById(USER_ID);
            verify(userRepository, never()).delete(any(User.class));
        }
    }

    @Nested
    @DisplayName("changePassword")
    class ChangePassword {

        @Test
        @DisplayName("changePassword: should encode and save new password when current password matches")
        void changePassword_shouldEncodeAndSave_whenCurrentPasswordMatches() {
            User user = user(USER_ID, "user", "user@example.com");
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(PASSWORD, user.getPassword())).thenReturn(true);
            when(passwordEncoder.encode("new-password")).thenReturn(ENCODED_PASSWORD);

            sut.changePassword(
                    USER_ID, new UpdatePasswordRequest(PASSWORD, "new-password")
            );

            assertThat(user.getPassword()).isEqualTo(ENCODED_PASSWORD);
            verify(passwordEncoder).matches(PASSWORD, "stored-password");
            verify(passwordEncoder).encode("new-password");
            verify(userRepository).save(user);
        }

        @Test
        @DisplayName("changePassword: should reject incorrect current password without saving")
        void changePassword_shouldReject_whenCurrentPasswordDoesNotMatch() {
            User user = user(USER_ID, "user", "user@example.com");
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(PASSWORD, user.getPassword())).thenReturn(false);

            assertThatThrownBy(() -> sut.changePassword(
                    USER_ID, new UpdatePasswordRequest(PASSWORD, "new-password")
            ))
                    .isInstanceOf(InvalidUserArgumentException.class)
                    .hasMessage("Current password is incorrect");

            verify(passwordEncoder).matches(PASSWORD, "stored-password");
            verify(passwordEncoder, never()).encode(any());
            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        @DisplayName("changePassword: should reject unchanged password without saving")
        void changePassword_shouldReject_whenNewPasswordEqualsCurrentPassword() {
            User user = user(USER_ID, "user", "user@example.com");
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(PASSWORD, user.getPassword())).thenReturn(true);

            assertThatThrownBy(() -> sut.changePassword(
                    USER_ID, new UpdatePasswordRequest(PASSWORD, PASSWORD)
            ))
                    .isInstanceOf(InvalidUserArgumentException.class)
                    .hasMessage("New password must be different from current password");

            verify(passwordEncoder).matches(PASSWORD, "stored-password");
            verify(passwordEncoder, never()).encode(any());
            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        @DisplayName("changePassword: should reject new password over 72 UTF-8 bytes without saving")
        void changePassword_shouldRejectPasswordOver72Utf8Bytes_whenPasswordIsMultibyte() {
            User user = user(USER_ID, "user", "user@example.com");
            String tooLongPassword = "界".repeat(25);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(PASSWORD, user.getPassword())).thenReturn(true);

            assertThatThrownBy(() -> sut.changePassword(
                    USER_ID, new UpdatePasswordRequest(PASSWORD, tooLongPassword)
            ))
                    .isInstanceOf(InvalidUserArgumentException.class)
                    .hasMessage("Password must not exceed 72 UTF-8 bytes");

            verify(passwordEncoder, never()).encode(any());
            verify(userRepository, never()).save(any(User.class));
        }
    }

    @Nested
    @DisplayName("findUserById")
    class FindUserById {

        @Test
        @DisplayName("findUserById: should return user when ID is positive and user exists")
        void findUserById_shouldReturnUser_whenIdIsPositiveAndUserExists() {
            User user = user(USER_ID, "user", "user@example.com");
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

            assertThat(sut.findUserById(USER_ID)).isSameAs(user);
            verify(userRepository).findById(USER_ID);
        }

        @Test
        @DisplayName("findUserById: should reject null ID without repository access")
        void findUserById_shouldReject_whenIdIsNull() {
            assertThatThrownBy(() -> sut.findUserById(null))
                    .isInstanceOf(InvalidUserArgumentException.class)
                    .hasMessage("User ID must be positive");

            verifyNoInteractions(userRepository);
        }

        @Test
        @DisplayName("findUserById: should reject non-positive ID without repository access")
        void findUserById_shouldReject_whenIdIsNonPositive() {
            assertThatThrownBy(() -> sut.findUserById(0L))
                    .isInstanceOf(InvalidUserArgumentException.class)
                    .hasMessage("User ID must be positive");

            assertThatThrownBy(() -> sut.findUserById(-1L))
                    .isInstanceOf(InvalidUserArgumentException.class)
                    .hasMessage("User ID must be positive");

            verifyNoInteractions(userRepository);
        }

        @Test
        @DisplayName("findUserById: should throw when user does not exist")
        void findUserById_shouldThrow_whenUserDoesNotExist() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> sut.findUserById(USER_ID))
                    .isInstanceOf(UserNotFoundException.class)
                    .hasMessage("User not found");

            verify(userRepository).findById(USER_ID);
        }
    }

    @Nested
    @DisplayName("findUserByUsernameOrEmail")
    class FindUserByUsernameOrEmail {

        @Test
        @DisplayName("findUserByUsernameOrEmail: should trim identifier before lookup")
        void findUserByUsernameOrEmail_shouldTrimIdentifier_whenIdentifierHasWhitespace() {
            User user = user(USER_ID, "user", "user@example.com");
            when(userRepository.findByUsernameIgnoreCaseOrEmailIgnoreCase("user", "user"))
                    .thenReturn(Optional.of(user));

            assertThat(sut.findUserByUsernameOrEmail("  user  "))
                    .containsSame(user);

            verify(userRepository)
                    .findByUsernameIgnoreCaseOrEmailIgnoreCase("user", "user");
        }

        @Test
        @DisplayName("findUserByUsernameOrEmail: should return empty without lookup for null or blank identifier")
        void findUserByUsernameOrEmail_shouldReturnEmpty_whenIdentifierIsNullOrBlank() {
            assertThat(sut.findUserByUsernameOrEmail(null)).isEmpty();
            assertThat(sut.findUserByUsernameOrEmail(" \t ")).isEmpty();

            verifyNoInteractions(userRepository);
        }
    }

    private static RegisterRequest request(String username, String email, String password) {
        return RegisterRequest.builder()
                .username(username)
                .email(email)
                .password(password)
                .build();
    }

    private static User user(Long id, String username, String email) {
        return User.builder()
                .id(id)
                .username(username)
                .email(email)
                .password("stored-password")
                .role(UserRole.USER)
                .build();
    }

    private static UserResponse response(Long id, String username, String email) {
        return new UserResponse(id, username, email, UserRole.USER, null, null, null);
    }
}