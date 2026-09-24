package com.study.grabthisforme.service.view;

import java.util.List;

public record PurchaseRecordView(
    String recordId,
    String purchaseId,
    Long buyerId,
    String buyerName,
    String buyerAvatarUrl,
    Long storeId,
    String storeName,
    Long goodsId,
    String goodsName,
    String goodsMessage,
    String goodsPic,
    Integer quantity,
    Double unitPrice,
    Double subtotalAmount,
    Double discountAmount,
    Double totalAmount,
    String userCouponId,
    Long createdTime,
    String status
) {

    public record PurchaseResultView(
        String purchaseId,
        Double subtotalAmount,
        Double discountAmount,
        Double totalAmount,
        String userCouponId,
        Long createdTime,
        List<PurchaseRecordView> records
    ) {
    }
}
