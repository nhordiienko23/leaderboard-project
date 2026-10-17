package com.github.nhordiienko23.leaderboardservice.config;

import com.github.nhordiienko23.leaderboardcontract.event.ActionType;
import com.github.nhordiienko23.leaderboardcontract.event.EventType;
import com.github.nhordiienko23.leaderboardcontract.event.ParticipantRole;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
@ConfigurationProperties(prefix = "leaderboard.scoring")
@Getter
@Setter
public class LeaderboardScoringProperties {

    private List<Rule> rules = new ArrayList<>();

    private Map<RuleKey, Long> pointsByRule = Map.of();

    @PostConstruct
    void initialize() {
        Map<RuleKey, Long> configuredRules = new HashMap<>();

        for (Rule rule : rules) {
            Objects.requireNonNull(rule.getEventType(), "Event type is required");
            Objects.requireNonNull(rule.getActionType(), "Action type is required");
            Objects.requireNonNull(
                    rule.getParticipantRole(),
                    "Participant role is required"
            );
            Objects.requireNonNull(rule.getPoints(), "Points are required");

            RuleKey key = new RuleKey(
                    rule.getEventType(),
                    rule.getActionType(),
                    rule.getParticipantRole()
            );

            if (configuredRules.putIfAbsent(key, rule.getPoints()) != null) {
                throw new IllegalStateException(
                        "Duplicate leaderboard scoring rule: " + key
                );
            }
        }

        pointsByRule = Map.copyOf(configuredRules);
    }

    public long pointsFor(
            EventType eventType,
            ActionType actionType,
            ParticipantRole participantRole
    ) {
        RuleKey key = new RuleKey(eventType, actionType, participantRole);
        Long points = pointsByRule.get(key);

        if (points == null) {
            throw new IllegalArgumentException(
                    "Scoring rule not configured: " + key
            );
        }

        return points;
    }

    @Getter
    @Setter
    public static class Rule {
        private EventType eventType;
        private ActionType actionType;
        private ParticipantRole participantRole;
        private Long points;
    }

    private record RuleKey(
            EventType eventType,
            ActionType actionType,
            ParticipantRole participantRole
    ) {
    }
}
