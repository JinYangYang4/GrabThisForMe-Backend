package com.study.grabthisforme.persistence.repository;

import com.study.grabthisforme.persistence.entity.FriendRequestEntity;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface FriendRequestRepository extends JpaRepository<FriendRequestEntity, String> {
  Optional<FriendRequestEntity> findByPendingPair(String pair);

  @Query(
      "select r from FriendRequestEntity r where (r.senderId=:uid or r.receiverId=:uid) and"
          + " (:before is null or r.createdAt<:before or (r.createdAt=:before and r.requestId<:id))"
          + " order by r.createdAt desc,r.requestId desc")
  List<FriendRequestEntity> page(
      @Param("uid") long uid, @Param("before") Long before, @Param("id") String id, Pageable page);
}
