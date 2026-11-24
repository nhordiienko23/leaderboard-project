package com.github.nhordiienko23.postservice.controller;

import com.github.nhordiienko23.postservice.entity.Comment;
import com.github.nhordiienko23.postservice.entity.Post;
import com.github.nhordiienko23.postservice.enums.UserRole;
import com.github.nhordiienko23.postservice.repository.CommentRepository;
import com.github.nhordiienko23.postservice.repository.PostRepository;
import com.github.nhordiienko23.postservice.service.CommentService;
import com.github.nhordiienko23.postservice.service.LikeService;
import com.github.nhordiienko23.postservice.service.PostService;
import com.github.nhordiienko23.postservice.testcontainers.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ProfileActivityIntegrationTest extends AbstractIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired PostRepository posts;
    @Autowired CommentRepository comments;
    @Autowired LikeService likeService;
    @Autowired PostService postService;
    @Autowired CommentService commentService;

    @Test
    void countsExistingTargetsAndReflectsRemovals() throws Exception {
        long author = 900001L;
        long other = 900002L;
        Post post = posts.saveAndFlush(Post.builder().userId(author).content("post").build());
        Comment comment = comments.saveAndFlush(Comment.builder().userId(author).post(post).content("comment").build());
        likeService.likePost(author, post.getId());
        likeService.likePost(other, post.getId());
        likeService.likeComment(author, post.getId(), comment.getId());
        likeService.likeComment(other, post.getId(), comment.getId());

        assertCounts(author, 1, 1, 1, 1, 2, 2);
        assertCounts(other, 0, 0, 1, 1, 0, 0);
        assertCounts(900003L, 0, 0, 0, 0, 0, 0);
        likeService.unlikePost(other, post.getId());
        likeService.unlikeComment(other, post.getId(), comment.getId());
        assertCounts(author, 1, 1, 1, 1, 1, 1);
        commentService.deleteComment(author, UserRole.USER, post.getId(), comment.getId());
        assertCounts(author, 1, 0, 1, 0, 1, 0);
        postService.deletePost(author, UserRole.USER, post.getId());
        assertCounts(author, 0, 0, 0, 0, 0, 0);
    }

    @Test
    void rejectsInvalidIdsAndMissingIdentity() throws Exception {
        mvc.perform(get("/api/posts/users/0/activity").header("X-User-Id", 1))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/posts/users/-1/activity").header("X-User-Id", 1))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/posts/users/abc/activity").header("X-User-Id", 1))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/posts/users/1/activity"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deletingPostRemovesItsCommentsAndBothLikeTargets() throws Exception {
        long author = 900011L;
        long commenter = 900012L;
        Post post = posts.saveAndFlush(Post.builder().userId(author).content("post").build());
        Comment comment = comments.saveAndFlush(Comment.builder().userId(commenter).post(post).content("comment").build());
        likeService.likePost(commenter, post.getId());
        likeService.likeComment(author, post.getId(), comment.getId());
        assertCounts(author, 1, 0, 0, 1, 1, 0);
        assertCounts(commenter, 0, 1, 1, 0, 0, 1);
        postService.deletePost(author, UserRole.USER, post.getId());
        assertCounts(author, 0, 0, 0, 0, 0, 0);
        assertCounts(commenter, 0, 0, 0, 0, 0, 0);
    }

    private void assertCounts(long userId, int postCount, int commentCount,
                              int postGiven, int commentGiven, int postReceived, int commentReceived) throws Exception {
        mvc.perform(get("/api/posts/users/{id}/activity", userId).header("X-User-Id", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postsCreated").value(postCount))
                .andExpect(jsonPath("$.commentsAuthored").value(commentCount))
                .andExpect(jsonPath("$.postLikesGiven").value(postGiven))
                .andExpect(jsonPath("$.commentLikesGiven").value(commentGiven))
                .andExpect(jsonPath("$.postLikesReceived").value(postReceived))
                .andExpect(jsonPath("$.commentLikesReceived").value(commentReceived));
    }
}
