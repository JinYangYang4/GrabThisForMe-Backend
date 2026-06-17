package com.study.grabthisforme.service.view;

public record OrderView(
    String orderId,
    UserView sender,
    UserView buyer,
    GoodsView goods,
    String shelfNumber,
    String aimPosition,
    String atPosition,
    Long startTime,
    Long endTime,
    Integer orderStatus,
    Boolean isAccepted
) {
}
