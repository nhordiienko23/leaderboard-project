package com.github.nhordiienko23.postservice.controller;

import com.github.nhordiienko23.postservice.entity.Comment;
import com.github.nhordiienko23.postservice.entity.Like;
import com.github.nhordiienko23.postservice.entity.Post;
import com.github.nhordiienko23.postservice.repository.CommentRepository;
import com.github.nhordiienko23.postservice.repository.LikeRepository;
import com.github.nhordiienko23.postservice.repository.OutboxRepository;
import com.github.nhordiienko23.postservice.repository.PostRepository;
import com.github.nhordiienko23.postservice.testcontainers.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("Engagement Summary Controller Integration Tests")
class EngagementSummaryControllerIntegrationTest
        extends AbstractIntegrationTest {

    private static final Long POST_AUTHOR_ID = 10L;
    private static final Long COMMENT_AUTHOR_ID = 20L;
    private static final Long VIEWER_ID = 30L;
    private static final Long OTHER_VIEWER_ID = 40L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private LikeRepository likeRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    @BeforeEach
    void cleanDatabase() {
        likeRepository.deleteAll();
        commentRepository.deleteAll();
        postRepository.deleteAll();
        outboxRepository.deleteAll();
    }

    @Test
    @DisplayName("Anonymous post summary returns zero counts and null viewer state")
    void anonymousPostSummaryShouldReturnZeroCounts() throws Exception {
        Post post = savePost("Empty post");

        mockMvc.perform(get(postUrl(post.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likeCount").value(0))
                .andExpect(jsonPath("$.commentCount").value(0))
                .andExpect(jsonPath("$.likedByMe").value(nullValue()));
    }

    @Test
    @DisplayName("Post summary returns nonzero counts and authenticated viewer state")
    void postSummaryShouldReturnCountsAndViewerState() throws Exception {
        Post post = savePost("Popular post");
        saveComment(post, "First comment");
        saveComment(post, "Second comment");
        savePostLike(post, VIEWER_ID);
        savePostLike(post, OTHER_VIEWER_ID);

        mockMvc.perform(get(postUrl(post.getId()))
                        .header("X-User-Id", VIEWER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likeCount").value(2))
                .andExpect(jsonPath("$.commentCount").value(2))
                .andExpect(jsonPath("$.likedByMe").value(true));

        mockMvc.perform(get(postUrl(post.getId()))
                        .header("X-User-Id", COMMENT_AUTHOR_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likedByMe").value(false));
    }

    @Test
    @DisplayName("Anonymous comment summary returns zero likes and null viewer state")
    void anonymousCommentSummaryShouldReturnZeroLikes() throws Exception {
        Post post = savePost("Post");
        Comment comment = saveComment(post, "Comment");

        mockMvc.perform(get(commentUrl(post.getId(), comment.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likeCount").value(0))
                .andExpect(jsonPath("$.likedByMe").value(nullValue()));
    }

    @Test
    @DisplayName("Comment summary returns nonzero likes and authenticated viewer state")
    void commentSummaryShouldReturnLikesAndViewerState() throws Exception {
        Post post = savePost("Post");
        Comment comment = saveComment(post, "Comment");
        saveCommentLike(comment, VIEWER_ID);
        saveCommentLike(comment, OTHER_VIEWER_ID);

        mockMvc.perform(get(commentUrl(post.getId(), comment.getId()))
                        .header("X-User-Id", VIEWER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likeCount").value(2))
                .andExpect(jsonPath("$.likedByMe").value(true));

        mockMvc.perform(get(commentUrl(post.getId(), comment.getId()))
                        .header("X-User-Id", POST_AUTHOR_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likedByMe").value(false));
    }

    @Test
    @DisplayName("Post summary returns not found for a missing post")
    void postSummaryShouldRejectMissingPost() throws Exception {
        mockMvc.perform(get(postUrl(Long.MAX_VALUE)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message")
                        .value("Post with id=" + Long.MAX_VALUE
                                + " not found"));
    }

    @Test
    @DisplayName("Comment summary returns not found for a missing post")
    void commentSummaryShouldRejectMissingPost() throws Exception {
        mockMvc.perform(get(commentUrl(Long.MAX_VALUE, 1L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message")
                        .value("Post with id=" + Long.MAX_VALUE
                                + " not found"));
    }

    @Test
    @DisplayName("Comment summary returns not found for a missing comment")
    void commentSummaryShouldRejectMissingComment() throws Exception {
        Post post = savePost("Post");

        mockMvc.perform(get(commentUrl(post.getId(), Long.MAX_VALUE)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message")
                        .value("Comment " + Long.MAX_VALUE
                                + " was not found for post " + post.getId()));
    }

    @Test
    @DisplayName("Comment summary rejects a comment belonging to another post")
    void commentSummaryShouldRejectCommentFromAnotherPost()
            throws Exception {

        Post firstPost = savePost("First post");
        Post secondPost = savePost("Second post");
        Comment comment = saveComment(secondPost, "Second post comment");

        mockMvc.perform(get(commentUrl(firstPost.getId(), comment.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message")
                        .value("Comment " + comment.getId()
                                + " was not found for post "
                                + firstPost.getId()));
    }

    private Post savePost(String content) {
        return postRepository.save(
                Post.builder()
                        .content(content)
                        .userId(POST_AUTHOR_ID)
                        .build()
        );
    }

    private Comment saveComment(Post post, String content) {
        return commentRepository.save(
                Comment.builder()
                        .content(content)
                        .userId(COMMENT_AUTHOR_ID)
                        .post(post)
                        .build()
        );
    }

    private void savePostLike(Post post, Long userId) {
        likeRepository.save(
                Like.builder()
                        .userId(userId)
                        .post(post)
                        .build()
        );
    }

    private void saveCommentLike(Comment comment, Long userId) {
        likeRepository.save(
                Like.builder()
                        .userId(userId)
                        .comment(comment)
                        .build()
        );
    }

    private String postUrl(Long postId) {
        return "/api/posts/" + postId + "/engagement";
    }

    private String commentUrl(Long postId, Long commentId) {
        return "/api/posts/" + postId
                + "/comments/" + commentId
                + "/engagement";
    }
}
