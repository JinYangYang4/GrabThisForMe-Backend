package com.study.grabthisforme.controller;

import com.study.grabthisforme.common.ApiResponse;
import com.study.grabthisforme.common.AuthContext;
import com.study.grabthisforme.service.UserService;
import com.study.grabthisforme.service.view.PostView;
import com.study.grabthisforme.service.view.UserBriefView;
import com.study.grabthisforme.service.view.UserGoodsSummaryView;
import com.study.grabthisforme.service.view.UserStoreSummaryView;
import com.study.grabthisforme.service.view.UserView;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public ApiResponse<List<UserBriefView>> listUsers(@RequestParam(required = false) String keyword) {
        return ApiResponse.success(userService.listUsers(keyword));
    }

    @GetMapping("/me")
    public ApiResponse<UserView> currentUser() {
        return ApiResponse.success(userService.getUser(AuthContext.requireUserId()));
    }

    @GetMapping("/{userId}")
    public ApiResponse<UserView> getUser(@PathVariable long userId) {
        return ApiResponse.success(userService.getUser(userId));
    }

    @GetMapping("/{userId}/posts")
    public ApiResponse<List<PostView.PostSummaryView>> listUserPosts(@PathVariable long userId) {
        return ApiResponse.success(userService.listUserPosts(userId));
    }

    @GetMapping("/{userId}/likes/posts")
    public ApiResponse<List<PostView.PostSummaryView>> listLikedPosts(@PathVariable long userId) {
        return ApiResponse.success(userService.listLikedPosts(userId));
    }

    @GetMapping("/{userId}/likes/stores")
    public ApiResponse<List<UserStoreSummaryView>> listLikedStores(@PathVariable long userId) {
        return ApiResponse.success(userService.listLikedStores(userId));
    }

    @GetMapping("/{userId}/likes/goods")
    public ApiResponse<List<UserGoodsSummaryView>> listLikedGoods(@PathVariable long userId) {
        return ApiResponse.success(userService.listLikedGoods(userId));
    }

    @PutMapping("/me")
    public ApiResponse<UserView> updateProfile(@jakarta.validation.Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.success(userService.updateProfile(
            AuthContext.requireUserId(),
            request.name(),
            request.avatarUrl(),
            request.phone(),
            request.email(),
            request.gender(),
            null,
            request.signature()
        ));
    }

    public record UpdateProfileRequest(
        @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=64) String name,
        @jakarta.validation.constraints.Size(max=255) String avatarUrl,
        @jakarta.validation.constraints.Size(max=32) String phone,
        @jakarta.validation.constraints.Email @jakarta.validation.constraints.Size(max=128) String email,
        @jakarta.validation.constraints.Min(0) @jakarta.validation.constraints.Max(2) Integer gender,
        Boolean isVip,
        @jakarta.validation.constraints.Size(max=200) String signature
    ) {
    }
}
