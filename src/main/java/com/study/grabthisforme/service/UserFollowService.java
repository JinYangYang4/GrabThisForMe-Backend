package com.study.grabthisforme.service;

import com.study.grabthisforme.common.ApiException;
import com.study.grabthisforme.persistence.entity.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserFollowService {
    private final EntityManager em;
    public UserFollowService(EntityManager em) { this.em = em; }

    @Transactional(readOnly = true)
    public boolean isFollowing(long userId, long targetId) {
        requireProfile(targetId, false);
        return em.find(UserFollowEntity.class, userId + ":" + targetId) != null;
    }

    @Transactional
    public boolean setFollowing(long userId, long targetId, boolean following) {
        if (userId == targetId) throw new ApiException(HttpStatus.BAD_REQUEST, 400, "不能关注自己");
        // Lock both endpoints in stable order: repeated/concurrent writes remain idempotent.
        requireProfile(Math.min(userId, targetId), true);
        requireProfile(Math.max(userId, targetId), true);
        var relation = em.find(UserFollowEntity.class, userId + ":" + targetId);
        // Repeated requests must not change either count.
        if (following == (relation != null)) return following;
        if (following) em.persist(new UserFollowEntity(userId, targetId));
        else em.remove(relation);
        var mine = statistics(userId);
        var theirs = statistics(targetId);
        long delta = following ? 1L : -1L;
        mine.followCount = Math.max(0L, valueOrZero(mine.followCount) + delta);
        theirs.fanCount = Math.max(0L, valueOrZero(theirs.fanCount) + delta);
        // Flush relation and counter changes together, also serving setRelationship's readback.
        em.flush();
        return following;
    }

    public record Relationship(boolean following, boolean followsMe, boolean mutual) {}
    public record FollowUser(Long id, String name, String headPic, long followedAt,
        boolean following, boolean followsMe, boolean mutual) {}
    public record FollowPage(java.util.List<FollowUser> items, Long nextBeforeTime, Long nextBeforeId, boolean hasMore) {}
    public record FollowCounts(long following, long followers) {}

    @Transactional(readOnly = true)
    public FollowCounts counts(long viewer) {
        var stats = em.find(UserStatisticsEntity.class, viewer);
        return stats == null ? new FollowCounts(0L, 0L) :
            new FollowCounts(valueOrZero(stats.followCount), valueOrZero(stats.fanCount));
    }

    @Transactional(readOnly = true)
    public Relationship relationship(long viewer, long target) {
        requireProfile(target, false);
        boolean following = em.find(UserFollowEntity.class, viewer + ":" + target) != null;
        boolean followsMe = em.find(UserFollowEntity.class, target + ":" + viewer) != null;
        return new Relationship(following, followsMe, following && followsMe);
    }

    @Transactional
    public Relationship setRelationship(long viewer, long target, boolean following) {
        setFollowing(viewer, target, following);
        return relationship(viewer, target);
    }

    @Transactional(readOnly = true)
    public FollowPage list(long viewer, boolean followers, Long beforeTime, Long beforeId, int requestedLimit) {
        if ((beforeTime == null) != (beforeId == null) || (beforeTime != null && (beforeTime < 0 || beforeId <= 0)))
            throw new ApiException(HttpStatus.BAD_REQUEST, 400, "beforeTime and beforeId must form a valid cursor");
        int limit = Math.max(1, Math.min(50, requestedLimit));
        String owner = followers ? "targetId" : "userId";
        String peer = followers ? "userId" : "targetId";
        String cursor = beforeTime == null ? "" : " and (f.createdAt < :time or (f.createdAt = :time and f." + peer + " < :peer))";
        var query = em.createQuery("select f from UserFollowEntity f where f." + owner + " = :viewer" + cursor +
            " order by f.createdAt desc, f." + peer + " desc", UserFollowEntity.class).setParameter("viewer", viewer);
        if (beforeTime != null) query.setParameter("time", beforeTime).setParameter("peer", beforeId);
        var relations = query.setMaxResults(limit + 1).getResultList();
        boolean more = relations.size() > limit;
        var page = relations.subList(0, Math.min(limit, relations.size()));
        if (page.isEmpty()) return new FollowPage(java.util.List.of(), null, null, false);
        var ids = page.stream().map(f -> followers ? f.userId : f.targetId).toList();
        var profiles = em.createQuery("select p from UserProfileEntity p where p.userId in :ids", UserProfileEntity.class)
            .setParameter("ids", ids).getResultList().stream().collect(java.util.stream.Collectors.toMap(p -> p.userId, p -> p));
        // The current page establishes one direction; query the reverse direction in one batch.
        String reversePeer = followers ? "targetId" : "userId";
        String reverseOwner = followers ? "userId" : "targetId";
        var reverse = new java.util.HashSet<>(em.createQuery("select f." + reversePeer + " from UserFollowEntity f where f." +
            reverseOwner + "=:viewer and f." + reversePeer + " in :ids", Long.class)
            .setParameter("viewer", viewer).setParameter("ids", ids).getResultList());
        var items = page.stream().map(f -> {
            long id = followers ? f.userId : f.targetId;
            var profile = profiles.get(id);
            boolean following = !followers || reverse.contains(id);
            boolean followsMe = followers || reverse.contains(id);
            return new FollowUser(id, profile == null ? "用户已注销" : profile.displayName,
                profile == null ? "" : profile.avatarUrl, f.createdAt, following, followsMe, following && followsMe);
        }).toList();
        var last = page.get(page.size()-1);
        return new FollowPage(items, more ? last.createdAt : null, more ? (followers ? last.userId : last.targetId) : null, more);
    }

    private static long valueOrZero(Long value) { return value == null ? 0L : value; }

    private void requireProfile(long id, boolean lock) {
        var profile = lock ? em.find(UserProfileEntity.class, id, LockModeType.PESSIMISTIC_WRITE)
                           : em.find(UserProfileEntity.class, id);
        if (profile == null) throw new ApiException(HttpStatus.NOT_FOUND, 404, "用户不存在");
    }

    private UserStatisticsEntity statistics(long id) {
        var stats = em.find(UserStatisticsEntity.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (stats == null) {
            stats = new UserStatisticsEntity(id, 0L, 0L, 0L);
            em.persist(stats);
        }
        return stats;
    }
}
