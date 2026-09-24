package com.study.grabthisforme.service.view;

public final class CouponView {
    private CouponView() {
    }

    public record TemplateView(
        Long templateId,
        String title,
        String description,
        Double discountAmount,
        Double minimumAmount,
        Double purchasePrice,
        Integer validDays,
        Long storeId,
        Integer stock,
        Integer perUserLimit,
        Long purchasedCount,
        Boolean canPurchase
    ) {
    }

    public record UserCouponView(
        String userCouponId,
        Long templateId,
        String title,
        String description,
        Double discountAmount,
        Double minimumAmount,
        Long storeId,
        Double purchasePricePaid,
        Long acquiredAt,
        Long validFrom,
        Long validUntil,
        String status,
        String usedPurchaseId,
        Boolean applicable
    ) {
    }

    public record CouponPurchaseView(
        String purchaseStatus,
        Double paidAmount,
        UserCouponView coupon
    ) {
    }
}
