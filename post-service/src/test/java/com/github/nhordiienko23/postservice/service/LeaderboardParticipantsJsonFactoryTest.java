package com.github.nhordiienko23.postservice.service;

import com.github.nhordiienko23.leaderboardcontract.event.ParticipantRole;
import com.github.nhordiienko23.leaderboardcontract.event.ScoreParticipant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeaderboardParticipantsJsonFactoryTest {

    private static final Long POST_AUTHOR_ID = 23L;
    private static final Long COMMENT_AUTHOR_ID = 1L;
    private static final Long LIKER_ID = 2L;

    private static final String SERIALIZED_PARTICIPANTS =
            "[{\"userId\":1,\"role\":\"COMMENT_AUTHOR\"}]";

    @Mock
    private JsonMapper jsonMapper;

    private LeaderboardParticipantsJsonFactory factory;

    @BeforeEach
    void setUp() {
        factory = new LeaderboardParticipantsJsonFactory(jsonMapper);
    }

    // -------------------------------------------------------------------------
    // POST
    // -------------------------------------------------------------------------

    @Test
    void forPostAuthor_shouldSerializePostAuthorParticipant() {
        when(jsonMapper.writeValueAsString(any()))
                .thenReturn(SERIALIZED_PARTICIPANTS);

        String result = factory.forPostAuthor(POST_AUTHOR_ID);

        assertEquals(SERIALIZED_PARTICIPANTS, result);

        verify(jsonMapper).writeValueAsString(List.of(
                new ScoreParticipant(
                        POST_AUTHOR_ID,
                        ParticipantRole.POST_AUTHOR
                )
        ));
    }

    @Test
    void forPostAuthor_shouldRejectNullAuthorId() {
        assertThrows(
                NullPointerException.class,
                () -> factory.forPostAuthor(null)
        );

        verifyNoInteractions(jsonMapper);
    }

    // -------------------------------------------------------------------------
    // COMMENT
    // -------------------------------------------------------------------------

    @Test
    void forComment_shouldSerializeCommentAuthorAndPostAuthor() {
        when(jsonMapper.writeValueAsString(any()))
                .thenReturn(SERIALIZED_PARTICIPANTS);

        String result = factory.forComment(
                COMMENT_AUTHOR_ID,
                POST_AUTHOR_ID
        );

        assertEquals(SERIALIZED_PARTICIPANTS, result);

        verify(jsonMapper).writeValueAsString(List.of(
                new ScoreParticipant(
                        COMMENT_AUTHOR_ID,
                        ParticipantRole.COMMENT_AUTHOR
                ),
                new ScoreParticipant(
                        POST_AUTHOR_ID,
                        ParticipantRole.CONTENT_AUTHOR
                )
        ));
    }

    @Test
    void forComment_shouldRejectNullCommentAuthorId() {
        assertThrows(
                NullPointerException.class,
                () -> factory.forComment(null, POST_AUTHOR_ID)
        );

        verifyNoInteractions(jsonMapper);
    }

    @Test
    void forComment_shouldRejectNullPostAuthorId() {
        assertThrows(
                NullPointerException.class,
                () -> factory.forComment(COMMENT_AUTHOR_ID, null)
        );

        verifyNoInteractions(jsonMapper);
    }

    // -------------------------------------------------------------------------
    // LIKE
    // -------------------------------------------------------------------------

    @Test
    void forLike_shouldSerializeLikerAndContentAuthor() {
        when(jsonMapper.writeValueAsString(any()))
                .thenReturn(SERIALIZED_PARTICIPANTS);

        String result = factory.forLike(
                LIKER_ID,
                POST_AUTHOR_ID
        );

        assertEquals(SERIALIZED_PARTICIPANTS, result);

        verify(jsonMapper).writeValueAsString(List.of(
                new ScoreParticipant(
                        LIKER_ID,
                        ParticipantRole.LIKER
                ),
                new ScoreParticipant(
                        POST_AUTHOR_ID,
                        ParticipantRole.CONTENT_AUTHOR
                )
        ));
    }

    @Test
    void forLike_shouldRejectSelfLike() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> factory.forLike(POST_AUTHOR_ID, POST_AUTHOR_ID)
        );

        assertEquals(
                "Users cannot receive points for liking their own content",
                exception.getMessage()
        );

        verifyNoInteractions(jsonMapper);
    }

    @Test
    void forLike_shouldRejectNullLikerId() {
        assertThrows(
                NullPointerException.class,
                () -> factory.forLike(null, POST_AUTHOR_ID)
        );

        verifyNoInteractions(jsonMapper);
    }

    @Test
    void forLike_shouldRejectNullContentAuthorId() {
        assertThrows(
                NullPointerException.class,
                () -> factory.forLike(LIKER_ID, null)
        );

        verifyNoInteractions(jsonMapper);
    }

    // -------------------------------------------------------------------------
    // SERIALIZATION ERRORS
    // -------------------------------------------------------------------------

    @Test
    void forPostAuthor_shouldWrapSerializationFailure() {
        when(jsonMapper.writeValueAsString(any()))
                .thenThrow(new RuntimeException("Serialization failed"));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> factory.forPostAuthor(POST_AUTHOR_ID)
        );

        assertEquals(
                "Cannot serialize leaderboard participants",
                exception.getMessage()
        );

        assertTrue(exception.getCause() instanceof RuntimeException);
    }
}
