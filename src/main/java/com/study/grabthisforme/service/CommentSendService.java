package com.study.grabthisforme.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.study.grabthisforme.common.ApiException;
import com.study.grabthisforme.persistence.entity.CommentSendReceiptEntity;
import com.study.grabthisforme.persistence.repository.*;
import com.study.grabthisforme.service.view.PostView;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Account lock works across JVMs; unique constraint and transaction protect durable receipts. */
@Service
public class CommentSendService {
    private final PostService posts;
    private final UserAccountRepository users;
    private final CommentSendReceiptRepository receipts;
    private final PostCommentRepository comments;
    private final PostReplyRepository replies;
    private final ViewAssembler views;
    private final ObjectMapper json;

    public CommentSendService(PostService posts, UserAccountRepository users, CommentSendReceiptRepository receipts,
            PostCommentRepository comments, PostReplyRepository replies, ViewAssembler views, ObjectMapper json) {
        this.posts = posts; this.users = users; this.receipts = receipts; this.comments = comments;
        this.replies = replies; this.views = views; this.json = json;
    }

    @Transactional
    public PostView.CommentView comment(long userId, String postId, String requestId, String message,
            List<String> images, String province) {
        if (requestId == null) return posts.addComment(userId, postId, message, images, province); // old APKs
        validate(requestId, message, images);
        String hash = hash(Arrays.asList("COMMENT", postId, message, images == null ? List.of() : images, province == null ? "" : province));
        var existing = receipt(userId, requestId, hash);
        if (existing != null) return decode(existing.resultJson, PostView.CommentView.class);
        var result = posts.addComment(userId, postId, message, images, province);
        var entity = comments.findById(result.commentId()).orElseThrow();
        entity.clientRequestId = requestId;
        comments.save(entity);
        result = views.toCommentView(entity, 0);
        save(userId, requestId, hash, "COMMENT", postId, result);
        return result;
    }

    @Transactional
    public PostView.ReplyView reply(long userId, String postId, String requestId, long parentCommentId,
            Long parentReplyId, String message, List<String> images, long beCommenterId) {
        if (requestId == null) return posts.addReply(userId, postId, parentCommentId, parentReplyId, message, images, beCommenterId);
        validate(requestId, message, images);
        String hash = hash(Arrays.asList("REPLY", postId, parentCommentId, parentReplyId, message,
            images == null ? List.of() : images, beCommenterId));
        var existing = receipt(userId, requestId, hash);
        if (existing != null) return decode(existing.resultJson, PostView.ReplyView.class);
        var parent = comments.findById(parentCommentId).orElseThrow(() -> error(HttpStatus.NOT_FOUND, 40443, "原评论不存在"));
        if (!postId.equals(parent.postId)) throw error(HttpStatus.BAD_REQUEST, 40043, "回复目标不属于当前话题");
        long expectedUser = parent.commenterId;
        if (parentReplyId != null) {
            var target = replies.findById(parentReplyId).orElseThrow(() -> error(HttpStatus.NOT_FOUND, 40444, "原回复不存在"));
            if (!postId.equals(target.postId) || target.parentCommentId != parentCommentId)
                throw error(HttpStatus.BAD_REQUEST, 40043, "回复目标不属于当前评论");
            expectedUser = target.commenterId;
        }
        if (expectedUser != beCommenterId) throw error(HttpStatus.BAD_REQUEST, 40043, "回复对象不匹配");
        var result = posts.addReply(userId, postId, parentCommentId, parentReplyId, message, images, beCommenterId);
        var entity = replies.findById(result.replyId()).orElseThrow();
        entity.clientRequestId = requestId;
        replies.save(entity);
        result = views.toReplyView(entity);
        save(userId, requestId, hash, "REPLY", postId, result);
        return result;
    }

    private CommentSendReceiptEntity receipt(long userId, String requestId, String hash) {
        users.findForUpdate(userId).orElseThrow(() -> error(HttpStatus.UNAUTHORIZED, 40101, "请登录后发送"));
        var found = receipts.findByUserIdAndRequestId(userId, requestId).orElse(null);
        if (found != null && !found.payloadHash.equals(hash))
            throw error(HttpStatus.CONFLICT, 40941, "同一发送编号不能用于不同内容");
        return found;
    }

    private void validate(String requestId, String message, List<String> images) {
        if (!requestId.matches("[A-Za-z0-9:_-]{1,80}")) throw error(HttpStatus.BAD_REQUEST, 40041, "发送编号无效");
        if ((message == null || message.isBlank()) && (images == null || images.isEmpty()))
            throw error(HttpStatus.BAD_REQUEST, 40041, "评论内容不能为空");
        if (message != null && message.length() > 10000) throw error(HttpStatus.BAD_REQUEST, 40041, "评论内容过长");
    }

    private void save(long userId, String requestId, String hash, String kind, String postId, Object result) {
        var receipt = new CommentSendReceiptEntity();
        receipt.receiptId = UUID.randomUUID().toString(); receipt.userId = userId; receipt.requestId = requestId;
        receipt.payloadHash = hash; receipt.kind = kind; receipt.postId = postId;
        receipt.resultJson = encode(result); receipt.createdAt = System.currentTimeMillis();
        receipts.saveAndFlush(receipt); // same commit as comment, reply and count
    }
    private String hash(Object payload) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(encode(payload).getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private String encode(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception e) { throw new IllegalStateException("发送回执编码失败", e); }
    }
    private <T> T decode(String value, Class<T> type) {
        try { return json.readValue(value, type); }
        catch (Exception e) { throw new IllegalStateException("发送回执读取失败", e); }
    }
    private ApiException error(HttpStatus status, int code, String message) { return new ApiException(status, code, message); }
}
