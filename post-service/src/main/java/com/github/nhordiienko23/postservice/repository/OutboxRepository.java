package com.github.nhordiienko23.postservice.repository;

import com.github.nhordiienko23.postservice.entity.OutboxEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface OutboxRepository extends JpaRepository<OutboxEntry, Long> {
    @Transactional(readOnly = true)
    List<OutboxEntry> findTop100ByPublishedFalseOrderByIdAsc();
}
