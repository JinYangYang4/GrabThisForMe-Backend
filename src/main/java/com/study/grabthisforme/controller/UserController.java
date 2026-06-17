package com.study.grabthisforme.controller;

import com.study.grabthisforme.common.ApiResponse;
import com.study.grabthisforme.common.AuthContext;
import com.study.grabthisforme.service.UserService;
import com.study.grabthisforme.service.view.UserGoodsSummaryView;
import com.study.grabthisforme.service.view.UserPostSummaryView;
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
    public ApiResponse<List<UserView>> listUsers(@RequestParam(required = false) String keyword) {
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
    public ApiResponse<List<UserPostSummaryView>> listUserPosts(@PathVariable long userId) {
        return ApiResponse.success(userService.listUserPosts(userId));
    }

    @GetMapping("/{userId}/likes/posts")
    public ApiResponse<List<UserPostSummaryView>> listLikedPosts(@PathVariable long userId) {
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
    public ApiResponse<UserView> updateProfile(@RequestBody UpdateProfileRequest request) {
        return ApiResponse.success(userService.updateProfile(
            AuthContext.requireUserId(),
            request.name(),
            request.avatarUrl(),
            request.phone(),
            request.email(),
            request.gender(),
            request.isVip(),
            request.signature()
        ));
    }

    public record UpdateProfileRequest(
        String name,
        String avatarUrl,
        String phone,
        String email,
        Integer gender,
        Boolean isVip,
        String signature
    ) {
    }
}
