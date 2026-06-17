package com.study.grabthisforme.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "order_cache")
public class OrderEntity {

    @Id
    public String orderId;
    public Long senderId;
    public String senderName;
    public String senderAvatarUrl;
    public Long buyerId;
    public String buyerName;
    public String buyerAvatarUrl;
    public Long goodsId;
    public String goodsName;
    public String goodsMessage;
    public Double goodsPrice;
    public String goodsPic;
    public String shelfNumber;
    public String aimPosition;
    public String atPosition;
    public Long startTime;
    public Long endTime;
    public Integer orderStatus;
    public Boolean isAccepted;

    public OrderEntity() {
    }
}
