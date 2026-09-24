package com.study.grabthisforme.persistence.repository;
import com.study.grabthisforme.persistence.entity.MediaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
public interface MediaRepository extends JpaRepository<MediaEntity,String> {
    long countByOwnerIdAndCreatedTimeGreaterThan(Long ownerId, Long since);
    @org.springframework.data.jpa.repository.Query("""
        select m.mediaId from MediaEntity m
        where m.conversationId is not null
        and (m.deletionPending = true or coalesce(m.retentionTime, m.createdTime) < :cutoff)
        and (:afterId is null or m.mediaId > :afterId)
        order by m.mediaId
        """)
    java.util.List<String> findRetentionCandidates(
        @org.springframework.data.repository.query.Param("cutoff") long cutoff,
        @org.springframework.data.repository.query.Param("afterId") String afterId,
        org.springframework.data.domain.Pageable page);
}
