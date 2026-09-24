package com.study.grabthisforme.service.view;

import java.util.List;

public record UserPostSummaryView(
    String postId,
    String content,
    List<String> images,
    Long createTime,
    UserBriefView author,
    Integer likeCount,
    Integer commentCount
) {
}
