package com.github.nhordiienko23.postservice.kafka;

public final class KafkaTopics {

    private KafkaTopics() {
    }

    public static final String POST_EVENTS = "post-events";
    public static final String COMMENT_EVENTS = "comment-events";
    public static final String LIKE_EVENTS = "like-events";
}
