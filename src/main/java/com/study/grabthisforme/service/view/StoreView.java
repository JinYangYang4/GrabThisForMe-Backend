package com.study.grabthisforme.service.view;

import java.util.List;

public record StoreView(
    Long id,
    Long ownerId,
    String name,
    String type,
    String address,
    Double latitude,
    Double longitude,
    String phone,
    String businessHours,
    String minOrderAmount,
    String deliveryFee,
    Boolean isOpen,
    String pic,
    Float rating,
    Long salesVolume,
    List<String> tags,
    List<StoreGoodsCategoryView> categories
) {

    public record StoreGoodsCategoryView(
        Long groupId,
        String category,
        Integer sortOrder,
        List<GoodsView> goods
    ) {
    }
}
