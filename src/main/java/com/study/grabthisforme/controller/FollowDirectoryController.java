package com.study.grabthisforme.controller;

import com.study.grabthisforme.common.ApiResponse;
import com.study.grabthisforme.common.AuthContext;
import com.study.grabthisforme.service.UserFollowService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users/me")
public class FollowDirectoryController {
    private final UserFollowService service;
    public FollowDirectoryController(UserFollowService service) { this.service = service; }
    @GetMapping("/follow-counts")
    public ApiResponse<UserFollowService.FollowCounts> counts() {
        return ApiResponse.success(service.counts(AuthContext.requireUserId()));
    }
    @GetMapping("/following")
    public ApiResponse<UserFollowService.FollowPage> following(@RequestParam(required=false) Long beforeTime, @RequestParam(required=false) Long beforeId, @RequestParam(defaultValue="20") int limit) {
        return ApiResponse.success(service.list(AuthContext.requireUserId(), false, beforeTime, beforeId, limit));
    }
    @GetMapping("/followers")
    public ApiResponse<UserFollowService.FollowPage> followers(@RequestParam(required=false) Long beforeTime, @RequestParam(required=false) Long beforeId, @RequestParam(defaultValue="20") int limit) {
        return ApiResponse.success(service.list(AuthContext.requireUserId(), true, beforeTime, beforeId, limit));
    }
}
