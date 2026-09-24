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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/posts")
public class PostController {

    private final PostService postService;

    private final com.study.grabthisforme.service.PostSearchService postSearchService;

    private final com.study.grabthisforme.service.CommentSendService commentSends;

    public PostController(PostService postService, com.study.grabthisforme.service.PostSearchService postSearchService,
            com.study.grabthisforme.service.CommentSendService commentSends) {
        this.commentSends = commentSends;
        this.postService = postService;
        this.postSearchService = postSearchService;
    }

    @GetMapping("/send-capabilities")
    public ApiResponse<Integer> sendCapabilities() {
        AuthContext.requireUserId();
        return ApiResponse.success(1);
    }

    @GetMapping("/search")
    public ApiResponse<com.study.grabthisforme.service.PostSearchService.SearchPage> searchPosts(
        @RequestParam String keyword,
        @RequestParam(required = false) String categoryKey,
        @RequestParam(defaultValue = "20") int limit,
        @RequestParam(required = false) Long beforeTime,
        @RequestParam(required = false) String beforeId,
        @RequestParam(required = false) Double latitude,
        @RequestParam(required = false) Double longitude
    ) {
        AuthContext.requireUserId();
        return ApiResponse.success(postSearchService.search(keyword, categoryKey, limit,
            beforeTime, beforeId, latitude, longitude));
    }

    @GetMapping
    public ApiResponse<PageView<PostView.PostSummaryView>> listPosts(
        @RequestParam(defaultValue = "20") int limit,
        @RequestParam(required = false) Long beforeTime,
        @RequestParam(required = false) String categoryKey
    ) {
        return ApiResponse.success(postService.listPosts(
            AuthContext.requireUserId(),
            limit,
            beforeTime,
            categoryKey
        ));
    }

    @GetMapping("/{postId}")
    public ApiResponse<PostView> getPost(@PathVariable String postId) {
        return ApiResponse.success(postService.getPost(postId, AuthContext.requireUserId()));
    }

    @GetMapping("/{postId}/comments")
    public ApiResponse<PageView<PostView.CommentView>> getComments(
        @PathVariable String postId,
        @RequestParam(defaultValue = "20") int limit,
        @RequestParam(required = false) Long beforeTime
    ) {
        return ApiResponse.success(postService.getComments(postId, AuthContext.requireUserId(), limit, beforeTime));
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
            request.images(),
            request.videoUrl(),
            request.videoUrls(),
            request.categoryKey(),
            request.customTags(),
            request.latitude(),
            request.longitude(),
            request.country(),
            request.province(),
            request.city(),
            request.district(),
            request.locationLabel()
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
        return ApiResponse.success(commentSends.comment(
            AuthContext.requireUserId(),
            postId,
            request.clientRequestId(),
            request.message(),
            request.imageUrls(),
            request.commenterProvince()
        ));
    }

    @PostMapping("/{postId}/replies")
    public ApiResponse<PostView.ReplyView> addReply(
        @PathVariable String postId,
        @RequestBody CreateReplyRequest request
    ) {
        return ApiResponse.success(commentSends.reply(
            AuthContext.requireUserId(),
            postId,
            request.clientRequestId(),
            request.parentCommentId(),
            request.parentReplyId(),
            request.message(),
            request.imageUrls(),
            request.beCommenterId()
        ));
    }

    public record CreatePostRequest(
        @NotBlank(message = "content is required") String content,
        List<String> images,
        String videoUrl,
        @jakarta.validation.constraints.Size(max = 9) List<@NotBlank String> videoUrls,
        String categoryKey,
        List<String> customTags,
        Double latitude,
        Double longitude,
        String country,
        String province,
        String city,
        String district,
        String locationLabel
    ) {
    }

    public record LikeRequest(boolean liked) {
    }

    public record CreateCommentRequest(String message, List<String> imageUrls, String commenterProvince, String clientRequestId) {
    }

    public record CreateReplyRequest(
        long parentCommentId,
        Long parentReplyId,
        String message,
        List<String> imageUrls,
        long beCommenterId,
        String clientRequestId
    ) {
    }
}
