package com.github.nhordiienko23.userservice.repository;

import com.github.nhordiienko23.userservice.entity.UserEventOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserEventOutboxRepository
        extends JpaRepository<UserEventOutbox, Long> {

    List<UserEventOutbox> findTop100ByPublishedFalseOrderByIdAsc();
}