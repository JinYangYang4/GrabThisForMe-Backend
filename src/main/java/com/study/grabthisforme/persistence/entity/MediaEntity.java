package com.study.grabthisforme.persistence.entity;
import jakarta.persistence.*;
@Entity @Table(name="media_asset", indexes=@Index(columnList="ownerId,createdTime"))
public class MediaEntity {
    @Id public String mediaId;
    public Long ownerId;
    public String conversationId;
    public String contentType;
    public Long createdTime;
    public Long byteSize;
    // Null on legacy rows; cleanup then falls back to createdTime.
    public Long retentionTime;
    // Committed before removing files; failed removals are retried after restart.
    public Boolean deletionPending;
}
