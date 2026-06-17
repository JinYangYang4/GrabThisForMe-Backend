package com.study.grabthisforme.service.view;

public record GoodsView(
    Long id,
    Long storeId,
    String name,
    String message,
    String categoryKey,
    PriceView price,
    UiView ui,
    StateView state,
    SecondhandView secondhand
) {

    public record PriceView(Double price, Double discountPrice, String discountTag) {
    }

    public record UiView(String pic, String tag, String unit) {
    }

    public record StateView(
        Long saleNumber,
        Integer stock,
        Boolean isSoldOut,
        Boolean isHot,
        Integer purchaseStatus,
        Long soldCount
    ) {
    }

    public record SecondhandView(
        Long saleUserId,
        Double originalPrice,
        String quality,
        String usedTime,
        Integer tradeStatus,
        Boolean negotiable
    ) {
    }
}
