package com.study.grabthisforme.controller;

import com.study.grabthisforme.common.ApiResponse;
import com.study.grabthisforme.common.AuthContext;
import com.study.grabthisforme.service.SocialService;
import com.study.grabthisforme.service.view.FriendRequestView;
import com.study.grabthisforme.service.view.GroupView;
import com.study.grabthisforme.service.view.FriendView;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/social")
public class SocialController {

    private final SocialService socialService;

    public SocialController(SocialService socialService) {
        this.socialService = socialService;
    }

    @GetMapping("/friends")
    public ApiResponse<List<FriendView>> listFriends() {
        return ApiResponse.success(socialService.listFriends(AuthContext.requireUserId()));
    }

    @GetMapping("/friend-requests")
    public ApiResponse<List<FriendRequestView>> listFriendRequests(@RequestParam(required=false) Long beforeTime, @RequestParam(required=false) String beforeId, @RequestParam(defaultValue="100") int limit) {
        return ApiResponse.success(socialService.listFriendRequests(AuthContext.requireUserId(),beforeTime,beforeId,limit));
    }

    @PostMapping("/friends/{friendUserId}")
    public ApiResponse<Void> addFriend(@PathVariable long friendUserId) {
        socialService.addFriend(AuthContext.requireUserId(), friendUserId);
        return ApiResponse.successMessage("friend request sent");
    }

    @PostMapping("/friend-requests/{requestId}/accept")
    public ApiResponse<Void> acceptFriend(@PathVariable String requestId) {
        socialService.acceptFriendRequest(AuthContext.requireUserId(), requestId);
        return ApiResponse.successMessage("friend request accepted");
    }

    @GetMapping("/groups")
    public ApiResponse<List<GroupView>> listGroups() {
        return ApiResponse.success(socialService.listGroups(AuthContext.requireUserId()));
    }

    @GetMapping("/groups/search")
    public ApiResponse<List<GroupView>> searchGroups(@RequestParam(required = false) String keyword) {
        return ApiResponse.success(socialService.searchGroups(keyword));
    }

    @PostMapping("/groups/{groupId}/join")
    public ApiResponse<Void> joinGroup(@PathVariable long groupId) {
        socialService.joinGroup(AuthContext.requireUserId(), groupId);
        return ApiResponse.successMessage("group joined");
    }

    @PostMapping("/groups")
    public ApiResponse<GroupView> createGroup(@org.springframework.web.bind.annotation.RequestHeader("Idempotency-Key") String key, @RequestBody CreateGroupRequest request) {
        return ApiResponse.success(socialService.createGroup(
            AuthContext.requireUserId(),
            request.groupName(),
            request.memberIds(), key
        ));
    }

    @PostMapping("/friend-requests/{requestId}/reject")
    public ApiResponse<Void> reject(@PathVariable String requestId) { socialService.rejectFriendRequest(AuthContext.requireUserId(),requestId);return ApiResponse.successMessage("rejected"); }

    public record CreateGroupRequest(String groupName, List<Long> memberIds) {
    }
}
