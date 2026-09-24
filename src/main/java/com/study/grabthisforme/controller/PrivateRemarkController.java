package com.study.grabthisforme.controller;

import com.study.grabthisforme.common.ApiResponse;
import com.study.grabthisforme.common.AuthContext;
import com.study.grabthisforme.service.PrivateRemarkService;
import com.study.grabthisforme.service.PrivateRemarkService.RemarkView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.*;

@RestController
public class PrivateRemarkController {
    private final PrivateRemarkService remarks;
    public PrivateRemarkController(PrivateRemarkService remarks) { this.remarks = remarks; }

    @PostMapping("/api/social/friends/{friendUserId}/remark")
    public ApiResponse<RemarkView> friend(@PathVariable long friendUserId, @Valid @RequestBody RemarkRequest request) {
        return ApiResponse.success(remarks.setFriendRemark(AuthContext.requireUserId(), friendUserId, request.remark()));
    }

    @PostMapping("/api/conversations/{conversationId}/group-remark")
    public ApiResponse<RemarkView> group(@PathVariable String conversationId, @Valid @RequestBody RemarkRequest request) {
        return ApiResponse.success(remarks.setGroupRemark(AuthContext.requireUserId(), conversationId, request.remark()));
    }

    public record RemarkRequest(@NotNull(message = "remark is required; use empty text to clear") String remark) {}
}
