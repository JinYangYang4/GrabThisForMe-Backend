package com.study.grabthisforme.controller;

import com.study.grabthisforme.common.ApiResponse;
import com.study.grabthisforme.common.AuthContext;
import com.study.grabthisforme.service.PostService;
import com.study.grabthisforme.service.view.PageView;
import com.study.grabthisforme.service.view.PostView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/posts")
public class PostController {

    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    @GetMapping
    public ApiResponse<List<PostView>> listPosts() {
        return ApiResponse.success(postService.listPosts(AuthContext.requireUserId()));
    }

    @GetMapping("/{postId}")
    public ApiResponse<PostView> getPost(@PathVariable String postId) {
        return ApiResponse.success(postService.getPost(postId, AuthContext.requireUserId()));
    }

    @GetMapping("/{postId}/comments")
    public ApiResponse<PageView<PostView.CommentView>> getComments(
        @PathVariable String postId,
        @RequestParam(defaultValue = "20") int limit,
        @RequestParam(defaultValue = "0") int offset
    ) {
        return ApiResponse.success(postService.getComments(postId, AuthContext.requireUserId(), limit, offset));
    }

    @GetMapping("/{postId}/comments/{commentId}/replies")
    public ApiResponse<PageView<PostView.ReplyView>> getReplies(
        @PathVariable String postId,
        @PathVariable long commentId,
        @RequestParam(defaultValue = "20") int limit,
        @RequestParam(required = false) Long beforeTime
    ) {
        return ApiResponse.success(postService.getReplies(postId, commentId, AuthContext.requireUserId(), limit, beforeTime));
    }

    @PostMapping
    public ApiResponse<PostView> createPost(@Valid @RequestBody CreatePostRequest request) {
        return ApiResponse.success(postService.createPost(
            AuthContext.requireUserId(),
            request.content(),
            request.images()
        ));
    }

    @PostMapping("/{postId}/like")
    public ApiResponse<Boolean> setPostLiked(@PathVariable String postId, @RequestBody LikeRequest request) {
        return ApiResponse.success(postService.setPostLiked(AuthContext.requireUserId(), postId, request.liked()));
    }

    @PostMapping("/{postId}/comments")
    public ApiResponse<PostView.CommentView> addComment(
        @PathVariable String postId,
        @RequestBody CreateCommentRequest request
    ) {
        return ApiResponse.success(postService.addComment(
            AuthContext.requireUserId(),
            postId,
            request.message(),
            request.imageUrls()
        ));
    }

    @PostMapping("/{postId}/replies")
    public ApiResponse<PostView.ReplyView> addReply(
        @PathVariable String postId,
        @RequestBody CreateReplyRequest request
    ) {
        return ApiResponse.success(postService.addReply(
            AuthContext.requireUserId(),
            postId,
            request.parentCommentId(),
            request.parentReplyId(),
            request.message(),
            request.imageUrls(),
            request.beCommenterId()
        ));
    }

    public record CreatePostRequest(
        @NotBlank(message = "content is required") String content,
        List<String> images
    ) {
    }

    public record LikeRequest(boolean liked) {
    }

    public record CreateCommentRequest(String message, List<String> imageUrls) {
    }

    public record CreateReplyRequest(
        long parentCommentId,
        Long parentReplyId,
        String message,
        List<String> imageUrls,
        long beCommenterId
    ) {
    }
}
