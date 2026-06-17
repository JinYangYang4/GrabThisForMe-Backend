package com.study.grabthisforme.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "goods_ui")
public class GoodsUiEntity {

    @Id
    public Long goodsId;
    public String pic;
    public String tag;
    public String unit;

    public GoodsUiEntity() {
    }

    public GoodsUiEntity(Long goodsId, String pic, String tag, String unit) {
        this.goodsId = goodsId;
        this.pic = pic;
        this.tag = tag;
        this.unit = unit;
    }
}
