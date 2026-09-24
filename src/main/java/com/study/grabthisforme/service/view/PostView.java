package com.study.grabthisforme.service.view;

import java.util.List;

public record PostView(
    String postId,
    String content,
    List<String> images,
    String videoUrl,
    List<String> videoUrls,
    Long createTime,
    String categoryKey,
    List<String> customTags,
    UserBriefView author,
    Integer likeCount,
    Integer commentCount,
    Boolean likedByCurrentUser,
    Double latitude,
    Double longitude,
    String country,
    String province,
    String city,
    String district,
    String locationLabel
) {

    public record PostSummaryView(
        String postId,
        String content,
        List<String> images,
    String videoUrl,
    List<String> videoUrls,
        Long createTime,
        String categoryKey,
        List<String> customTags,
        UserBriefView author,
        Integer likeCount,
        Integer commentCount,
        Double latitude,
        Double longitude,
        String country,
        String province,
        String city,
        String district,
        String locationLabel
    ) {
    }

    public record CommentView(
        Long commentId,
        Long time,
        String message,
        List<String> imageUrls,
        UserBriefView commenter,
        Integer replyCount,
        String commenterProvince,
        String clientRequestId
    ) {
    }

    public record ReplyView(
        Long replyId,
        Long parentCommentId,
        Long parentReplyId,
        Long time,
        String message,
        List<String> imageUrls,
        UserBriefView commenter,
        UserBriefView beCommenter,
        String clientRequestId
    ) {
    }
}
