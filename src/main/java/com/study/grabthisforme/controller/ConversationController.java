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
    public ApiResponse<List<MessageView>> listMessages(@PathVariable String conversationId) {
        return ApiResponse.success(conversationService.listMessages(AuthContext.requireUserId(), conversationId));
    }

    @PostMapping("/single")
    public ApiResponse<ConversationView> createSingleConversation(@Valid @RequestBody CreateSingleRequest request) {
        return ApiResponse.success(conversationService.createSingleConversation(
            AuthContext.requireUserId(),
            request.peerUserId()
        ));
    }

    @PostMapping("/group")
    public ApiResponse<ConversationView> createGroupConversation(@RequestBody CreateGroupRequest request) {
        return ApiResponse.success(conversationService.createGroupConversation(
            AuthContext.requireUserId(),
            request.groupName(),
            request.memberIds()
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
            request.type(),
            request.content(),
            request.mediaUrl()
        ));
    }

    @PostMapping("/{conversationId}/read")
    public ApiResponse<Void> markRead(@PathVariable String conversationId) {
        conversationService.markRead(AuthContext.requireUserId(), conversationId);
        return ApiResponse.successMessage("conversation marked as read");
    }

    @PostMapping("/{conversationId}/hidden")
    public ApiResponse<Void> setHidden(@PathVariable String conversationId, @RequestBody HiddenRequest request) {
        conversationService.setHidden(AuthContext.requireUserId(), conversationId, request.hidden());
        return ApiResponse.successMessage("conversation hidden state updated");
    }

    public record CreateSingleRequest(@NotNull(message = "peerUserId is required") Long peerUserId) {
    }

    public record CreateGroupRequest(String groupName, List<Long> memberIds) {
    }

    public record SendMessageRequest(
        @NotBlank(message = "type is required") String type,
        String content,
        String mediaUrl
    ) {
    }

    public record HiddenRequest(boolean hidden) {
    }
}
