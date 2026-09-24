package com.study.grabthisforme.service.view;
import com.study.grabthisforme.persistence.entity.OrderEntity;
public record OrderView(String orderId, Long senderId, String senderName, String senderAvatarUrl,
    Long buyerId, String buyerName, String buyerAvatarUrl, Long goodsId, String goodsName,
    String goodsMessage, Double goodsPrice, String goodsPic, String shelfNumber, String aimPosition,
    String atPosition, Long startTime, Long endTime, Integer orderStatus, Boolean isAccepted, Integer quantity,
    String errandType, String recipientName, String recipientPhone) {
    public static OrderView from(OrderEntity e, boolean participant) {
        return new OrderView(e.orderId, e.senderId, text(e.senderName), text(e.senderAvatarUrl),
            e.buyerId, text(e.buyerName), text(e.buyerAvatarUrl), e.goodsId == null ? 0L : e.goodsId,
            text(e.goodsName), participant ? text(e.goodsMessage) : "接单后查看详细说明",
            e.goodsPrice == null ? 0.0 : e.goodsPrice, text(e.goodsPic),
            participant ? text(e.shelfNumber) : "", participant ? text(e.aimPosition) : "接单后查看",
            participant ? text(e.atPosition) : "接单后查看", e.startTime, e.endTime, e.orderStatus, e.isAccepted,
            e.quantity==null?1:e.quantity, resolveErrandType(e),
            participant ? text(e.recipientName) : "", participant ? text(e.recipientPhone) : "");
    }
    private static String resolveErrandType(OrderEntity entity) {
        if ("BUY_GOODS".equals(entity.errandType) || "PICKUP_EXPRESS".equals(entity.errandType)) {
            return entity.errandType;
        }
        return text(entity.goodsName).startsWith("快递代取") ? "PICKUP_EXPRESS" : "BUY_GOODS";
    }
    private static String text(String value) { return value == null ? "" : value; }
}
