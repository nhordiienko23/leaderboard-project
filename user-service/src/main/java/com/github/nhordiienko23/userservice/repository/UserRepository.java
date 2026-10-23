package com.github.nhordiienko23.userservice.repository;

import com.github.nhordiienko23.userservice.entity.User;
import com.github.nhordiienko23.userservice.exception.UserAlreadyExistsException;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByUsernameIgnoreCaseAndIdNot(String username, Long id);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    Optional<User> findByUsernameIgnoreCaseOrEmailIgnoreCase(
            String username,
            String email
    );

    Optional<User> findByEmailIgnoreCase(String email);


    default void validateUsername(String username) {
        if (existsByUsernameIgnoreCase(username)) {
            throw new UserAlreadyExistsException("Username already exists");
        }
    }

    default void validateEmail(String email) {
        if (existsByEmailIgnoreCase(email)) {
            throw new UserAlreadyExistsException("Email already exists");
        }
    }

    default void validateUsernameForUpdate(String username, Long userId) {
        if (existsByUsernameIgnoreCaseAndIdNot(username, userId)) {
            throw new UserAlreadyExistsException("Username already exists");
        }
    }

    default void validateEmailForUpdate(String email, Long userId) {
        if (existsByEmailIgnoreCaseAndIdNot(email, userId)) {
            throw new UserAlreadyExistsException("Email already exists");
        }
    }
}