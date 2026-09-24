package com.study.grabthisforme.controller;

import com.study.grabthisforme.common.ApiResponse;
import com.study.grabthisforme.common.AuthContext;
import com.study.grabthisforme.service.CouponService;
import com.study.grabthisforme.service.view.CouponView.CouponPurchaseView;
import com.study.grabthisforme.service.view.CouponView.TemplateView;
import com.study.grabthisforme.service.view.CouponView.UserCouponView;
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
@RequestMapping("/api/coupons")
public class CouponController {
    private final CouponService couponService;

    public CouponController(CouponService couponService) {
        this.couponService = couponService;
    }

    @GetMapping("/market")
    public ApiResponse<List<TemplateView>> listMarket() {
        return ApiResponse.success(couponService.listMarket(AuthContext.requireUserId()));
    }

    @GetMapping("/mine")
    public ApiResponse<List<UserCouponView>> listMine() {
        return ApiResponse.success(couponService.listMine(AuthContext.requireUserId()));
    }

    @GetMapping("/applicable")
    public ApiResponse<List<UserCouponView>> listApplicable(
        @RequestParam long storeId,
        @RequestParam double orderAmount
    ) {
        return ApiResponse.success(
            couponService.listApplicable(AuthContext.requireUserId(), storeId, orderAmount)
        );
    }

    @PostMapping("/{templateId}/purchase")
    public ApiResponse<CouponPurchaseView> buyCoupon(
        @PathVariable long templateId,
        @Valid @RequestBody BuyCouponRequest request
    ) {
        return ApiResponse.success(
            couponService.buyCoupon(AuthContext.requireUserId(), templateId, request.clientRequestId())
        );
    }

    public record BuyCouponRequest(
        @NotBlank(message = "clientRequestId is required") String clientRequestId
    ) {
    }
}
