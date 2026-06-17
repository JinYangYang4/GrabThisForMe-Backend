package com.study.grabthisforme.controller;

import com.study.grabthisforme.common.ApiResponse;
import com.study.grabthisforme.common.AuthContext;
import com.study.grabthisforme.service.StoreService;
import com.study.grabthisforme.service.view.StoreView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stores")
public class StoreController {

    private final StoreService storeService;

    public StoreController(StoreService storeService) {
        this.storeService = storeService;
    }

    @GetMapping
    public ApiResponse<List<StoreView>> listStores(@RequestParam(required = false) String keyword) {
        return ApiResponse.success(storeService.listStores(keyword));
    }

    @GetMapping("/{storeId}")
    public ApiResponse<StoreView> getStore(@PathVariable long storeId) {
        return ApiResponse.success(storeService.getStore(storeId));
    }

    @PostMapping
    public ApiResponse<StoreView> createStore(@Valid @RequestBody CreateStoreRequest request) {
        return ApiResponse.success(storeService.createStore(
            AuthContext.requireUserId(),
            request.name(),
            request.type(),
            request.address(),
            request.latitude(),
            request.longitude(),
            request.phone(),
            request.businessHours(),
            request.minOrderAmount(),
            request.deliveryFee(),
            request.pic(),
            request.tags()
        ));
    }

    @PostMapping("/{storeId}/like")
    public ApiResponse<Boolean> setStoreLiked(@PathVariable long storeId, @RequestBody LikeRequest request) {
        return ApiResponse.success(storeService.setStoreLiked(AuthContext.requireUserId(), storeId, request.liked()));
    }

    public record CreateStoreRequest(
        @NotBlank(message = "name is required") String name,
        String type,
        @NotBlank(message = "address is required") String address,
        Double latitude,
        Double longitude,
        String phone,
        String businessHours,
        String minOrderAmount,
        String deliveryFee,
        String pic,
        List<String> tags
    ) {
    }

    public record LikeRequest(boolean liked) {
    }
}
