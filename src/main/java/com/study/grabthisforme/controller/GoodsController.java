package com.study.grabthisforme.controller;

import com.study.grabthisforme.common.ApiResponse;
import com.study.grabthisforme.common.AuthContext;
import com.study.grabthisforme.service.GoodsService;
import com.study.grabthisforme.service.view.GoodsView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/goods")
public class GoodsController {

    private final GoodsService goodsService;

    public GoodsController(GoodsService goodsService) {
        this.goodsService = goodsService;
    }

    @GetMapping
    public ApiResponse<List<GoodsView>> listGoods(
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) Long storeId,
        @RequestParam(defaultValue = "false") boolean secondhandOnly
    ) {
        return ApiResponse.success(goodsService.listGoods(keyword, storeId, secondhandOnly));
    }

    @GetMapping("/{goodsId}")
    public ApiResponse<GoodsView> getGoods(@PathVariable long goodsId) {
        return ApiResponse.success(goodsService.getGoods(goodsId));
    }

    @PostMapping
    public ApiResponse<GoodsView> createGoods(@Valid @RequestBody CreateGoodsRequest request) {
        return ApiResponse.success(goodsService.createGoods(
            AuthContext.requireUserId(),
            request.storeId(),
            request.name(),
            request.message(),
            request.categoryKey(),
            request.price(),
            request.discountPrice(),
            request.discountTag(),
            request.pic(),
            request.tag(),
            request.unit(),
            request.stock(),
            request.isHot(),
            request.secondhand(),
            request.originalPrice(),
            request.quality(),
            request.usedTime(),
            request.negotiable()
        ));
    }

    @PostMapping("/{goodsId}/like")
    public ApiResponse<Boolean> setGoodsLiked(@PathVariable long goodsId, @RequestBody LikeRequest request) {
        return ApiResponse.success(goodsService.setGoodsLiked(AuthContext.requireUserId(), goodsId, request.liked()));
    }

    public record CreateGoodsRequest(
        Long storeId,
        @NotBlank(message = "name is required") String name,
        String message,
        String categoryKey,
        @NotNull(message = "price is required") Double price,
        Double discountPrice,
        String discountTag,
        String pic,
        String tag,
        String unit,
        Integer stock,
        Boolean isHot,
        Boolean secondhand,
        Double originalPrice,
        String quality,
        String usedTime,
        Boolean negotiable
    ) {
    }

    public record LikeRequest(boolean liked) {
    }
}
