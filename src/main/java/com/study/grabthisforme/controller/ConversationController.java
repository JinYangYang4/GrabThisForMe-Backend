package com.study.grabthisforme.controller;

import com.study.grabthisforme.common.ApiResponse;
import com.study.grabthisforme.common.AuthContext;
import com.study.grabthisforme.service.ConversationService;
import com.study.grabthisforme.service.view.ConversationView;
import com.study.grabthisforme.service.view.MessageView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @GetMapping
    public ApiResponse<List<ConversationView>> listConversations() {
        return ApiResponse.success(conversationService.listConversations(AuthContext.requireUserId()));
    }

    @GetMapping("/{conversationId}/messages")
    public ApiResponse<List<MessageView>> listMessages(
        @PathVariable String conversationId,
        @RequestParam(required = false) Long beforeTime,
        @RequestParam(required = false) String beforeId,
        @RequestParam(defaultValue = "20") int limit
    ) {
        return ApiResponse.success(
            conversationService.listMessages(AuthContext.requireUserId(), conversationId, beforeTime, beforeId, limit)
        );
    }

    @PostMapping("/single")
    public ApiResponse<ConversationView> createSingleConversation(@Valid @RequestBody CreateSingleRequest request) {
        return ApiResponse.success(conversationService.createSingleConversation(
            AuthContext.requireUserId(),
            request.peerUserId()
        ));
    }

    @PostMapping("/group")
    public ApiResponse<ConversationView> createGroupConversation(@org.springframework.web.bind.annotation.RequestHeader("Idempotency-Key") String key, @RequestBody CreateGroupRequest request) {
        return ApiResponse.success(conversationService.createGroupConversation(
            AuthContext.requireUserId(),
            request.groupName(),
            request.memberIds(), key
        ));
    }

    @PostMapping("/group/{groupId}/open")
    public ApiResponse<ConversationView> openGroupConversation(@PathVariable Long groupId) {
        return ApiResponse.success(conversationService.openGroupConversation(
            AuthContext.requireUserId(),
            groupId
        ));
    }

    @PostMapping("/{conversationId}/messages")
    public ApiResponse<MessageView> sendMessage(
        @PathVariable String conversationId,
        @Valid @RequestBody SendMessageRequest request
    ) {
        return ApiResponse.success(conversationService.sendMessage(
            AuthContext.requireUserId(),
            conversationId,
            request.clientMsgId(),
            request.type(),
            request.content(),
            request.mediaUrl(),
            request.replyToMessageId()
        ));
    }

    @PostMapping("/{conversationId}/read")
    public ApiResponse<Void> markRead(
        @PathVariable String conversationId,
        @RequestBody(required = false) ReadConversationRequest request
    ) {
        conversationService.markRead(
            AuthContext.requireUserId(),
            conversationId,
            request == null ? null : request.lastReadTime()
        );
        return ApiResponse.successMessage("conversation marked as read");
    }

    @PostMapping("/{conversationId}/hidden")
    public ApiResponse<Void> setHidden(@PathVariable String conversationId, @Valid @RequestBody HiddenRequest request) {
        conversationService.setHidden(AuthContext.requireUserId(), conversationId, request.hidden());
        return ApiResponse.successMessage("conversation hidden state updated");
    }

    @PostMapping("/{conversationId}/pinned")
    public ApiResponse<com.study.grabthisforme.service.view.ConversationPinView> setPinned(
        @PathVariable String conversationId, @Valid @RequestBody PinnedRequest request
    ) {
        return ApiResponse.success(conversationService.setPinned(AuthContext.requireUserId(), conversationId, request.pinned()));
    }

    public record PinnedRequest(@NotNull(message = "pinned is required") Boolean pinned) {}

    public record CreateSingleRequest(@NotNull(message = "peerUserId is required") Long peerUserId) {
    }

    public record CreateGroupRequest(String groupName, List<Long> memberIds) {
    }

    public record SendMessageRequest(
        @NotBlank(message = "clientMsgId is required") String clientMsgId,
        @NotBlank(message = "type is required") String type,
        String content,
        String mediaUrl,
        String replyToMessageId
    ) {
        public SendMessageRequest(String clientMsgId, String type, String content, String mediaUrl) {
            this(clientMsgId, type, content, mediaUrl, null);
        }
    }

    public record HiddenRequest(@NotNull(message = "hidden is required") Boolean hidden) {
    }

    public record ReadConversationRequest(Long lastReadTime) {
    }
}
