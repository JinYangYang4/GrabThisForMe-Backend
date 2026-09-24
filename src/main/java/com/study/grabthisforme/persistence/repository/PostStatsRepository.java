package com.study.grabthisforme.persistence.repository;

import com.study.grabthisforme.persistence.entity.PostStatsEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostStatsRepository extends JpaRepository<PostStatsEntity, String> {
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("update PostStatsEntity s set s.commentCount=s.commentCount+1 where s.postId=:postId")
    int incrementCommentCount(@org.springframework.data.repository.query.Param("postId") String postId);

    List<PostStatsEntity> findAllByPostIdIn(List<String> postIds);
}
