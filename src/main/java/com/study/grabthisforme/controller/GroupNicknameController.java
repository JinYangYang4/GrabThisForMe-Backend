package com.study.grabthisforme.controller;

import com.study.grabthisforme.common.ApiResponse;
import com.study.grabthisforme.common.AuthContext;
import com.study.grabthisforme.service.GroupNicknameService;
import com.study.grabthisforme.service.GroupNicknameService.NicknameView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.*;

@RestController
public class GroupNicknameController {
    private final GroupNicknameService nicknames;
    public GroupNicknameController(GroupNicknameService nicknames) { this.nicknames = nicknames; }

    @PostMapping("/api/conversations/{conversationId}/my-nickname")
    public ApiResponse<NicknameView> update(@PathVariable String conversationId, @Valid @RequestBody NicknameRequest request) {
        return ApiResponse.success(nicknames.setMyNickname(AuthContext.requireUserId(), conversationId, request.nickname()));
    }

    public record NicknameRequest(@NotNull(message = "nickname is required; use empty text to clear") String nickname) {}
}
