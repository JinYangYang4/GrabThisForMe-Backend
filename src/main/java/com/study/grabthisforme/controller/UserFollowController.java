package com.study.grabthisforme.controller;

import com.study.grabthisforme.common.ApiResponse;
import com.study.grabthisforme.common.AuthContext;
import com.study.grabthisforme.service.UserFollowService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users/{userId}/follow")
public class UserFollowController {
    private final UserFollowService service;
    public UserFollowController(UserFollowService service) { this.service = service; }
    @GetMapping
    public ApiResponse<Boolean> status(@PathVariable long userId) {
        return ApiResponse.success(service.isFollowing(AuthContext.requireUserId(), userId));
    }
    @PutMapping
    public ApiResponse<Boolean> follow(@PathVariable long userId) {
        return ApiResponse.success(service.setFollowing(AuthContext.requireUserId(), userId, true));
    }
    @DeleteMapping
    public ApiResponse<Boolean> unfollow(@PathVariable long userId) {
        return ApiResponse.success(service.setFollowing(AuthContext.requireUserId(), userId, false));
    }
    @GetMapping("/relationship")
    public ApiResponse<UserFollowService.Relationship> relationship(@PathVariable long userId) {
        return ApiResponse.success(service.relationship(AuthContext.requireUserId(), userId));
    }
    @PutMapping("/relationship")
    public ApiResponse<UserFollowService.Relationship> followRelationship(@PathVariable long userId) {
        return ApiResponse.success(service.setRelationship(AuthContext.requireUserId(), userId, true));
    }
    @DeleteMapping("/relationship")
    public ApiResponse<UserFollowService.Relationship> unfollowRelationship(@PathVariable long userId) {
        return ApiResponse.success(service.setRelationship(AuthContext.requireUserId(), userId, false));
    }
}
