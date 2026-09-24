package com.study.grabthisforme.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "post_cache")
public class PostEntity {

    @Id
    public String postId;
    public String content;
    public String imagesJson;
    public String videoUrl;
    @jakarta.persistence.Column(length = 4096)
    public String videoUrlsJson;
    public String categoryKey;
    public Long createTime;
    public Double latitude;
    public Double longitude;
    public String country;
    public String province;
    public String city;
    public String district;
    public String locationLabel;

    public PostEntity() {
    }
}
