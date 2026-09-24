package com.study.grabthisforme.controller;

import com.study.grabthisforme.common.*;
import com.study.grabthisforme.service.MessageActionsService;
import com.study.grabthisforme.service.view.MessageView;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/conversations")
public class MessageActionsController {
    private final MessageActionsService actions;
    public MessageActionsController(MessageActionsService actions) { this.actions = actions; }
    @PostMapping("/{cid}/messages/{id}/recall")
    public ApiResponse<MessageView> recall(@PathVariable String cid, @PathVariable String id) {
        return ApiResponse.success(actions.recall(AuthContext.requireUserId(), cid, id));
    }
    @GetMapping("/{cid}/message-updates")
    public ApiResponse<List<MessageView>> updates(@PathVariable String cid,
        @RequestParam(defaultValue="0") long afterTime, @RequestParam(defaultValue="") String afterId) {
        return ApiResponse.success(actions.updates(AuthContext.requireUserId(), cid, afterTime, afterId));
    }
    @PostMapping("/{cid}/messages/forward")
    public ApiResponse<MessageView> forward(@PathVariable String cid, @RequestBody ForwardRequest body) {
        if (body.sourceConversationId() == null || body.sourceMessageId() == null)
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, 40066, "缺少原消息");
        return ApiResponse.success(actions.forward(AuthContext.requireUserId(), cid,
            body.sourceConversationId(), body.sourceMessageId(), body.clientMsgId()));
    }
    public record ForwardRequest(String sourceConversationId, String sourceMessageId, String clientMsgId) {}
}
