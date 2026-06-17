package com.study.grabthisforme.service;

import com.study.grabthisforme.common.ApiException;
import com.study.grabthisforme.common.IdGenerator;
import com.study.grabthisforme.common.Jsons;
import com.study.grabthisforme.persistence.entity.PostCommentEntity;
import com.study.grabthisforme.persistence.entity.PostEntity;
import com.study.grabthisforme.persistence.entity.PostReplyEntity;
import com.study.grabthisforme.persistence.entity.PostStatsEntity;
import com.study.grabthisforme.persistence.entity.UserLikedPostEntity;
import com.study.grabthisforme.persistence.entity.UserPostEntity;
import com.study.grabthisforme.persistence.repository.PostCommentRepository;
import com.study.grabthisforme.persistence.repository.PostRepository;
import com.study.grabthisforme.persistence.repository.PostReplyRepository;
import com.study.grabthisforme.persistence.repository.PostStatsRepository;
import com.study.grabthisforme.persistence.repository.UserLikedPostRepository;
import com.study.grabthisforme.persistence.repository.UserPostRepository;
import com.study.grabthisforme.service.view.PageView;
import com.study.grabthisforme.service.view.PostView;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class PostService {

    private final PostRepository postRepository;
    private final PostStatsRepository postStatsRepository;
    private final PostCommentRepository postCommentRepository;
    private final PostReplyRepository postReplyRepository;
    private final UserPostRepository userPostRepository;
    private final UserLikedPostRepository userLikedPostRepository;
    private final IdGenerator idGenerator;
    private final ViewAssembler viewAssembler;

    public PostService(
        PostRepository postRepository,
        PostStatsRepository postStatsRepository,
        PostCommentRepository postCommentRepository,
        PostReplyRepository postReplyRepository,
        UserPostRepository userPostRepository,
        UserLikedPostRepository userLikedPostRepository,
        IdGenerator idGenerator,
        ViewAssembler viewAssembler
    ) {
        this.postRepository = postRepository;
        this.postStatsRepository = postStatsRepository;
        this.postCommentRepository = postCommentRepository;
        this.postReplyRepository = postReplyRepository;
        this.userPostRepository = userPostRepository;
        this.userLikedPostRepository = userLikedPostRepository;
        this.idGenerator = idGenerator;
        this.viewAssembler = viewAssembler;
    }

    public List<PostView> listPosts(Long currentUserId) {
        return postRepository.findAllByOrderByCreateTimeDesc().stream()
            .map(entity -> viewAssembler.toPostView(entity, currentUserId))
            .toList();
    }

    public PostView getPost(String postId, Long currentUserId) {
        PostEntity post = postRepository.findById(postId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, 40441, "Post not found"));
        return viewAssembler.toPostView(post, currentUserId);
    }

    public PageView<PostView.CommentView> getComments(String postId, Long currentUserId, int limit, int offset) {
        postRepository.findById(postId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, 40441, "Post not found"));
        int safeLimit = Math.max(1, limit);
        int safeOffset = Math.max(0, offset);
        List<PostCommentEntity> commentEntities = postCommentRepository.findAllByPostIdOrderByTimeAsc(
            postId,
            PageRequest.of(safeOffset / safeLimit, safeLimit)
        );
        Map<Long, Long> replyCountByCommentId = commentEntities.stream()
            .collect(Collectors.toMap(
                entity -> entity.commentId,
                entity -> postReplyRepository.countByParentCommentId(entity.commentId)
            ));
        List<PostView.CommentView> items = commentEntities.stream()
            .map(comment -> viewAssembler.toCommentView(comment, replyCountByCommentId.getOrDefault(comment.commentId, 0L).intValue()))
            .toList();
        long total = postCommentRepository.countByPostId(postId);
        return new PageView<>(items, total, safeLimit, safeOffset, safeOffset + items.size() < total);
    }

    public PageView<PostView.ReplyView> getReplies(
        String postId,
        long commentId,
        Long currentUserId,
        int limit,
        Long beforeTime
    ) {
        postRepository.findById(postId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, 40441, "Post not found"));
        postCommentRepository.findById(commentId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, 40443, "Parent comment not found"));
        int safeLimit = Math.max(1, limit);
        long safeBeforeTime = beforeTime == null || beforeTime <= 0L
            ? System.currentTimeMillis() + 1L
            : beforeTime;
        List<PostReplyEntity> replyEntities = postReplyRepository.findByParentCommentIdAndTimeLessThanOrderByTimeDesc(
            commentId,
            safeBeforeTime,
            PageRequest.of(0, safeLimit)
        );
        List<PostView.ReplyView> items = replyEntities.stream()
            .map(viewAssembler::toReplyView)
            .toList();
        long total = postReplyRepository.countByParentCommentId(commentId);
        boolean hasMore = items.size() >= safeLimit;
        return new PageView<>(items, total, safeLimit, 0, hasMore);
    }

    @Transactional
    public PostView createPost(long userId, String content, List<String> images) {
        PostEntity post = new PostEntity();
        post.postId = idGenerator.nextPostId();
        post.content = content == null ? "" : content.trim();
        post.imagesJson = Jsons.writeStringList(images);
        post.createTime = System.currentTimeMillis();
        postRepository.save(post);
        PostStatsEntity stats = new PostStatsEntity();
        stats.postId = post.postId;
        stats.likeCount = 0;
        stats.commentCount = 0;
        postStatsRepository.save(stats);
        userPostRepository.save(new UserPostEntity(userId, post.postId));
        return viewAssembler.toPostView(post, userId);
    }

    @Transactional
    public boolean setPostLiked(long userId, String postId, boolean liked) {
        PostEntity post = postRepository.findById(postId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, 40441, "Post not found"));
        PostStatsEntity stats = postStatsRepository.findById(post.postId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, 40442, "Post stats not found"));
        UserLikedPostEntity existing = userLikedPostRepository.findByUserIdAndPostId(userId, postId).orElse(null);
        if (liked) {
            if (existing == null) {
                userLikedPostRepository.save(new UserLikedPostEntity(userId, postId, System.currentTimeMillis()));
                stats.likeCount = stats.likeCount + 1;
                postStatsRepository.save(stats);
            }
            return true;
        }
        if (existing != null) {
            userLikedPostRepository.delete(existing);
            stats.likeCount = Math.max(0, stats.likeCount - 1);
            postStatsRepository.save(stats);
        }
        return false;
    }

    @Transactional
    public PostView.CommentView addComment(long userId, String postId, String message, List<String> imageUrls) {
        postRepository.findById(postId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, 40441, "Post not found"));
        PostCommentEntity entity = new PostCommentEntity();
        entity.commentId = idGenerator.nextLongId();
        entity.postId = postId;
        entity.time = System.currentTimeMillis();
        entity.message = message;
        entity.imageUrlsJson = Jsons.writeStringList(imageUrls);
        entity.commenterId = userId;
        entity.commenterName = viewAssembler.getUserView(userId).name();
        entity.commenterAvatarUrl = viewAssembler.getUserView(userId).headPic();
        postCommentRepository.save(entity);
        PostStatsEntity stats = postStatsRepository.findById(postId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, 40442, "Post stats not found"));
        stats.commentCount = stats.commentCount + 1;
        postStatsRepository.save(stats);
        return viewAssembler.toCommentView(entity, 0);
    }

    @Transactional
    public PostView.ReplyView addReply(
        long userId,
        String postId,
        long parentCommentId,
        Long parentReplyId,
        String message,
        List<String> imageUrls,
        long beCommenterId
    ) {
        postRepository.findById(postId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, 40441, "Post not found"));
        postCommentRepository.findById(parentCommentId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, 40443, "Parent comment not found"));
        PostReplyEntity entity = new PostReplyEntity();
        entity.replyId = idGenerator.nextLongId();
        entity.postId = postId;
        entity.parentCommentId = parentCommentId;
        entity.parentReplyId = parentReplyId;
        entity.time = System.currentTimeMillis();
        entity.message = message;
        entity.imageUrlsJson = Jsons.writeStringList(imageUrls);
        entity.commenterId = userId;
        entity.commenterName = viewAssembler.getUserView(userId).name();
        entity.commenterAvatarUrl = viewAssembler.getUserView(userId).headPic();
        entity.beCommenterId = beCommenterId;
        entity.beCommenterName = viewAssembler.getUserView(beCommenterId).name();
        entity.beCommenterAvatarUrl = viewAssembler.getUserView(beCommenterId).headPic();
        postReplyRepository.save(entity);
        PostStatsEntity stats = postStatsRepository.findById(postId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, 40442, "Post stats not found"));
        stats.commentCount = stats.commentCount + 1;
        postStatsRepository.save(stats);
        return viewAssembler.toReplyView(entity);
    }
}
