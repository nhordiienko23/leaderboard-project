package com.github.nhordiienko23.userservice.service;

import com.github.nhordiienko23.leaderboardcontract.event.UserEventType;
import com.github.nhordiienko23.userservice.entity.UserEventOutbox;
import com.github.nhordiienko23.userservice.mapper.UserEventOutboxMapper;
import com.github.nhordiienko23.userservice.repository.UserEventOutboxRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserEventOutboxService Tests")
class UserEventOutboxServiceTest {

    @Mock
    private UserEventOutboxRepository outboxRepository;

    @Mock
    private UserEventOutboxMapper outboxMapper;

    @InjectMocks
    private UserEventOutboxService sut;

    @Nested
    @DisplayName("enqueue")
    class Enqueue {

        @Test
        @DisplayName("enqueue: should map and save the event when given valid inputs")
        void enqueue_shouldMapAndSave_whenGivenValidInputs() {
            Long userId = 42L;
            String username = "test-user";
            UserEventType eventType = UserEventType.CREATED;
            UserEventOutbox outbox = UserEventOutbox.builder()
                    .userId(userId)
                    .username(username)
                    .eventType(eventType)
                    .build();

            when(outboxMapper.toOutbox(userId, username, eventType))
                    .thenReturn(outbox);

            sut.enqueue(userId, username, eventType);

            verify(outboxMapper).toOutbox(userId, username, eventType);
            verify(outboxRepository).save(outbox);
            verifyNoMoreInteractions(outboxMapper, outboxRepository);
        }

        @Test
        @DisplayName("enqueue: should pass through null inputs when given null values")
        void enqueue_shouldPassThroughNullInputs_whenGivenNullValues() {
            UserEventOutbox outbox = UserEventOutbox.builder().build();

            when(outboxMapper.toOutbox(null, null, null)).thenReturn(outbox);

            sut.enqueue(null, null, null);

            verify(outboxMapper).toOutbox(null, null, null);
            verify(outboxRepository).save(outbox);
            verifyNoMoreInteractions(outboxMapper, outboxRepository);
        }

        @Test
        @DisplayName("enqueue: should preserve the username when given unusual text")
        void enqueue_shouldPreserveUsername_whenGivenUnusualText() {
            Long userId = 42L;
            String username = "雪'; DROP TABLE users; --" + "x".repeat(5000);
            UserEventType eventType = UserEventType.UPDATED;
            UserEventOutbox outbox = UserEventOutbox.builder().build();

            when(outboxMapper.toOutbox(userId, username, eventType))
                    .thenReturn(outbox);

            sut.enqueue(userId, username, eventType);

            verify(outboxMapper).toOutbox(userId, username, eventType);
            verify(outboxRepository).save(outbox);
            verifyNoMoreInteractions(outboxMapper, outboxRepository);
        }

        @Test
        @DisplayName("enqueue: should propagate mapper exceptions without saving")
        void enqueue_shouldPropagateMapperException_whenMappingFails() {
            Long userId = 42L;
            String username = "test-user";
            UserEventType eventType = UserEventType.CREATED;
            IllegalArgumentException failure =
                    new IllegalArgumentException("mapping failed");

            when(outboxMapper.toOutbox(userId, username, eventType))
                    .thenThrow(failure);

            assertThatThrownBy(() -> sut.enqueue(userId, username, eventType))
                    .isSameAs(failure)
                    .hasMessage("mapping failed");

            verify(outboxMapper).toOutbox(userId, username, eventType);
            verifyNoInteractions(outboxRepository);
            verifyNoMoreInteractions(outboxMapper);
        }

        @Test
        @DisplayName("enqueue: should propagate repository exceptions when saving fails")
        void enqueue_shouldPropagateRepositoryException_whenSavingFails() {
            Long userId = 42L;
            String username = "test-user";
            UserEventType eventType = UserEventType.CREATED;
            UserEventOutbox outbox = UserEventOutbox.builder().build();
            IllegalStateException failure =
                    new IllegalStateException("save failed");

            when(outboxMapper.toOutbox(userId, username, eventType))
                    .thenReturn(outbox);
            when(outboxRepository.save(outbox)).thenThrow(failure);

            assertThatThrownBy(() -> sut.enqueue(userId, username, eventType))
                    .isSameAs(failure)
                    .hasMessage("save failed");

            verify(outboxMapper).toOutbox(userId, username, eventType);
            verify(outboxRepository).save(outbox);
            verifyNoMoreInteractions(outboxMapper, outboxRepository);
        }
    }
}