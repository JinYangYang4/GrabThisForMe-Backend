package com.study.grabthisforme.service.view;

import java.util.List;

public record PostView(
    String postId,
    String content,
    List<String> images,
    Long createTime,
    UserView author,
    Integer likeCount,
    Integer commentCount,
    Boolean likedByCurrentUser
) {

    public record PostSummaryView(
        String postId,
        String content,
        List<String> images,
        Long createTime,
        Long authorId,
        String authorName,
        String authorAvatarUrl,
        Integer likeCount,
        Integer commentCount
    ) {
    }

    public record CommentView(
        Long commentId,
        Long time,
        String message,
        List<String> imageUrls,
        UserView commenter,
        Integer replyCount
    ) {
    }

    public record ReplyView(
        Long replyId,
        Long parentCommentId,
        Long parentReplyId,
        Long time,
        String message,
        List<String> imageUrls,
        UserView commenter,
        UserView beCommenter
    ) {
    }
}
