package com.study.grabthisforme.controller;

import com.study.grabthisforme.common.ApiResponse;
import com.study.grabthisforme.common.AuthContext;
import com.study.grabthisforme.service.SocialService;
import com.study.grabthisforme.service.view.GroupView;
import com.study.grabthisforme.service.view.UserView;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    public ApiResponse<List<UserView>> listFriends() {
        return ApiResponse.success(socialService.listFriends(AuthContext.requireUserId()));
    }

    @PostMapping("/friends/{friendUserId}")
    public ApiResponse<Void> addFriend(@PathVariable long friendUserId) {
        socialService.addFriend(AuthContext.requireUserId(), friendUserId);
        return ApiResponse.successMessage("friend added");
    }

    @GetMapping("/groups")
    public ApiResponse<List<GroupView>> listGroups() {
        return ApiResponse.success(socialService.listGroups(AuthContext.requireUserId()));
    }

    @PostMapping("/groups")
    public ApiResponse<GroupView> createGroup(@RequestBody CreateGroupRequest request) {
        return ApiResponse.success(socialService.createGroup(
            AuthContext.requireUserId(),
            request.groupName(),
            request.memberIds()
        ));
    }

    public record CreateGroupRequest(String groupName, List<Long> memberIds) {
    }
}
