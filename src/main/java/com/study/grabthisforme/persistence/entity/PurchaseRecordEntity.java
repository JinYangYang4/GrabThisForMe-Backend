package com.study.grabthisforme.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(
    name = "purchase_record",
    indexes = {
        @Index(name = "idx_purchase_buyer_time", columnList = "buyerId,createdTime"),
        @Index(name = "idx_purchase_buyer_client", columnList = "buyerId,clientPurchaseId")
    }
)
public class PurchaseRecordEntity {

    @Id
    public String recordId;
    public String purchaseId;
    public String clientPurchaseId;
    public Long buyerId;
    public String buyerName;
    public String buyerAvatarUrl;
    public Long storeId;
    public String storeName;
    public Long goodsId;
    public String goodsName;
    public String goodsMessage;
    public String goodsPic;
    public Integer quantity;
    public Double unitPrice;
    public Double subtotalAmount;
    public Double discountAmount;
    public Double totalAmount;
    public String userCouponId;
    public Long createdTime;
    public String status;

    public PurchaseRecordEntity() {
    }
}
