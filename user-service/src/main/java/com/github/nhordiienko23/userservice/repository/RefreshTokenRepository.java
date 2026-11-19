package com.github.nhordiienko23.userservice.repository;

import com.github.nhordiienko23.userservice.entity.RefreshToken;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository
        extends JpaRepository<RefreshToken, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select token
            from RefreshToken token
            where token.tokenHash = :tokenHash
            """)
    Optional<RefreshToken> findByTokenHashForUpdate(
            @Param("tokenHash") String tokenHash
    );

    @Modifying
    @Query("""
    delete from RefreshToken token
    where token.expiresAt <= :now
       or (
            token.revokedAt is not null
            and token.revokedAt <= :revokedBefore
       )
    """)
    int deleteExpiredOrOldRevoked(
            @Param("now") Instant now,
            @Param("revokedBefore") Instant revokedBefore
    );
}