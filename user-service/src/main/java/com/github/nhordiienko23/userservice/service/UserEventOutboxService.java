package com.github.nhordiienko23.userservice.service;

import com.github.nhordiienko23.leaderboardcontract.event.UserEventType;
import com.github.nhordiienko23.userservice.mapper.UserEventOutboxMapper;
import com.github.nhordiienko23.userservice.repository.UserEventOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserEventOutboxService {

    private final UserEventOutboxRepository outboxRepository;
    private final UserEventOutboxMapper outboxMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(Long userId, String username, UserEventType eventType) {
        outboxRepository.save(
                outboxMapper.toOutbox(userId, username, eventType)
        );
    }
}