package com.github.nhordiienko23.leaderboardservice.config;

import com.github.nhordiienko23.leaderboardcontract.event.ActionType;
import com.github.nhordiienko23.leaderboardcontract.event.EventType;
import com.github.nhordiienko23.leaderboardcontract.event.ParticipantRole;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class LeaderboardScoringPropertiesTest {

    @Test
    void shouldReturnConfiguredPointsForMatchingRule() {
        LeaderboardScoringProperties properties = propertiesWith(
                rule(EventType.POST, ActionType.CREATED,
                        ParticipantRole.POST_AUTHOR, 10L)
        );

        properties.initialize();

        assertThat(properties.pointsFor(
                EventType.POST,
                ActionType.CREATED,
                ParticipantRole.POST_AUTHOR
        )).isEqualTo(10L);
    }

    @Test
    void shouldReturnNegativePointsForDeletionRule() {
        LeaderboardScoringProperties properties = propertiesWith(
                rule(EventType.POST, ActionType.DELETED,
                        ParticipantRole.POST_AUTHOR, -10L)
        );

        properties.initialize();

        assertThat(properties.pointsFor(
                EventType.POST,
                ActionType.DELETED,
                ParticipantRole.POST_AUTHOR
        )).isEqualTo(-10L);
    }

    @Test
    void shouldThrowWhenScoringRuleDoesNotExist() {
        LeaderboardScoringProperties properties = propertiesWith(
                rule(EventType.POST, ActionType.CREATED,
                        ParticipantRole.POST_AUTHOR, 10L)
        );

        properties.initialize();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> properties.pointsFor(
                        EventType.LIKE,
                        ActionType.CREATED,
                        ParticipantRole.CONTENT_AUTHOR
                ))
                .withMessageContaining("Scoring rule not configured");
    }

    @Test
    void shouldRejectDuplicateScoringRules() {
        LeaderboardScoringProperties.Rule firstRule =
                rule(EventType.POST, ActionType.CREATED,
                        ParticipantRole.POST_AUTHOR, 10L);

        LeaderboardScoringProperties.Rule duplicateRule =
                rule(EventType.POST, ActionType.CREATED,
                        ParticipantRole.POST_AUTHOR, 20L);

        LeaderboardScoringProperties properties =
                propertiesWith(firstRule, duplicateRule);

        assertThatIllegalStateException()
                .isThrownBy(properties::initialize)
                .withMessageContaining("Duplicate leaderboard scoring rule");
    }

    @Test
    void shouldRejectRuleWithoutEventType() {
        LeaderboardScoringProperties.Rule rule =
                rule(null, ActionType.CREATED,
                        ParticipantRole.POST_AUTHOR, 10L);

        LeaderboardScoringProperties properties = propertiesWith(rule);

        assertThatNullPointerException()
                .isThrownBy(properties::initialize)
                .withMessage("Event type is required");
    }

    @Test
    void shouldRejectRuleWithoutActionType() {
        LeaderboardScoringProperties.Rule rule =
                rule(EventType.POST, null,
                        ParticipantRole.POST_AUTHOR, 10L);

        LeaderboardScoringProperties properties = propertiesWith(rule);

        assertThatNullPointerException()
                .isThrownBy(properties::initialize)
                .withMessage("Action type is required");
    }

    @Test
    void shouldRejectRuleWithoutParticipantRole() {
        LeaderboardScoringProperties.Rule rule =
                rule(EventType.POST, ActionType.CREATED,
                        null, 10L);

        LeaderboardScoringProperties properties = propertiesWith(rule);

        assertThatNullPointerException()
                .isThrownBy(properties::initialize)
                .withMessage("Participant role is required");
    }

    @Test
    void shouldRejectRuleWithoutPoints() {
        LeaderboardScoringProperties.Rule rule =
                rule(EventType.POST, ActionType.CREATED,
                        ParticipantRole.POST_AUTHOR, null);

        LeaderboardScoringProperties properties = propertiesWith(rule);

        assertThatNullPointerException()
                .isThrownBy(properties::initialize)
                .withMessage("Points are required");
    }

    private LeaderboardScoringProperties propertiesWith(
            LeaderboardScoringProperties.Rule... rules
    ) {
        LeaderboardScoringProperties properties =
                new LeaderboardScoringProperties();

        properties.setRules(List.of(rules));

        return properties;
    }

    private LeaderboardScoringProperties.Rule rule(
            EventType eventType,
            ActionType actionType,
            ParticipantRole participantRole,
            Long points
    ) {
        LeaderboardScoringProperties.Rule rule =
                new LeaderboardScoringProperties.Rule();

        rule.setEventType(eventType);
        rule.setActionType(actionType);
        rule.setParticipantRole(participantRole);
        rule.setPoints(points);

        return rule;
    }
}
