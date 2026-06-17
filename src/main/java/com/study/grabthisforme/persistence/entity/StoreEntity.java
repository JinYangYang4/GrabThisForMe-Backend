package com.study.grabthisforme.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "store_cache")
public class StoreEntity {

    @Id
    public Long storeId;
    public Long ownerId;
    public String name;
    public String type;
    public String address;
    public Double latitude;
    public Double longitude;
    public String phone;
    public String businessHours;
    public String minOrderAmount;
    public String deliveryFee;
    public Boolean isOpen;
    public String pic;
    public Float rating;
    public Long salesVolume;

    public StoreEntity() {
    }

    public StoreEntity(
        Long storeId,
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
        Long salesVolume
    ) {
        this.storeId = storeId;
        this.ownerId = ownerId;
        this.name = name;
        this.type = type;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.phone = phone;
        this.businessHours = businessHours;
        this.minOrderAmount = minOrderAmount;
        this.deliveryFee = deliveryFee;
        this.isOpen = isOpen;
        this.pic = pic;
        this.rating = rating;
        this.salesVolume = salesVolume;
    }
}
