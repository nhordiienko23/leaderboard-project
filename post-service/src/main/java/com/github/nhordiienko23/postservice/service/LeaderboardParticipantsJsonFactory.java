package com.github.nhordiienko23.postservice.service;

import com.github.nhordiienko23.leaderboardcontract.event.ParticipantRole;
import com.github.nhordiienko23.leaderboardcontract.event.ScoreParticipant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class LeaderboardParticipantsJsonFactory {

    private final JsonMapper jsonMapper;

    public String forPostAuthor(Long postAuthorId) {
        Objects.requireNonNull(
                postAuthorId,
                "Post author ID must not be null"
        );

        return serialize(List.of(
                new ScoreParticipant(
                        postAuthorId,
                        ParticipantRole.POST_AUTHOR
                )
        ));
    }

    public String forComment(
            Long commentAuthorId,
            Long postAuthorId
    ) {
        Objects.requireNonNull(
                commentAuthorId,
                "Comment author ID must not be null"
        );
        Objects.requireNonNull(
                postAuthorId,
                "Post author ID must not be null"
        );

        return serialize(List.of(
                new ScoreParticipant(
                        commentAuthorId,
                        ParticipantRole.COMMENT_AUTHOR
                ),
                new ScoreParticipant(
                        postAuthorId,
                        ParticipantRole.CONTENT_AUTHOR
                )
        ));
    }

    private String serialize(List<ScoreParticipant> participants) {
        try {
            return jsonMapper.writeValueAsString(participants);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Cannot serialize leaderboard participants",
                    e
            );
        }
    }
}