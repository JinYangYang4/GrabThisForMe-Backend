package com.study.grabthisforme.service.view;

public record UserGoodsSummaryView(
    BaseView base,
    PriceView price,
    UiView ui,
    StateView state
) {

    public record BaseView(
        Long id,
        Long storeId,
        String name,
        String message,
        String category
    ) {
    }

    public record PriceView(
        Double price,
        Double discountPrice,
        String discountTag
    ) {
    }

    public record UiView(
        String pic,
        String tag,
        String unit
    ) {
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
}
