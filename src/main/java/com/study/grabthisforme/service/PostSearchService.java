package com.study.grabthisforme.service;

import com.study.grabthisforme.common.ApiException;
import com.study.grabthisforme.persistence.entity.PostEntity;
import com.study.grabthisforme.persistence.entity.PostCustomTagEntity;
import com.study.grabthisforme.service.view.PostView;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PostSearchService {
    private final EntityManager entityManager;
    private final ViewAssembler assembler;

    public PostSearchService(EntityManager entityManager, ViewAssembler assembler) {
        this.entityManager = entityManager;
        this.assembler = assembler;
    }

    public record SearchPage(List<PostView.PostSummaryView> items, boolean hasMore,
                             Long nextBeforeTime, String nextBeforeId) {}

    @Transactional(readOnly = true)
    public SearchPage search(String keyword, String categoryKey, int limit,
                             Long beforeTime, String beforeId, Double latitude, Double longitude) {
        String term = keyword == null ? "" : keyword.strip();
        if (term.isEmpty() || term.length() > 80) {
            throw invalid("搜索关键词需为 1 至 80 个字符");
        }
        if ((beforeTime == null) != (beforeId == null)
            || (beforeTime != null && (beforeTime < 0 || beforeId.isBlank() || beforeId.length() > 128))) {
            throw invalid("分页游标无效");
        }
        if ((latitude == null) != (longitude == null)
            || (latitude != null && (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                || Math.abs(latitude) > 90 || Math.abs(longitude) > 180))) {
            throw invalid("定位参数无效");
        }
        int pageSize = Math.max(1, Math.min(limit, 50));
        var cb = entityManager.getCriteriaBuilder();
        var query = cb.createQuery(PostEntity.class);
        var post = query.from(PostEntity.class);
        // Escape LIKE metacharacters: user-entered % and _ must remain literal text.
        String pattern = "%" + term.toLowerCase(Locale.ROOT).replace("!", "!!")
            .replace("%", "!%").replace("_", "!_") + "%";
        var tags = query.subquery(Integer.class);
        var tag = tags.from(PostCustomTagEntity.class);
        tags.select(cb.literal(1)).where(cb.equal(tag.get("postId"), post.get("postId")),
            cb.like(cb.lower(tag.get("tag")), pattern, '!'));
        List<Predicate> filters = new ArrayList<>();
        filters.add(cb.or(cb.like(cb.lower(post.get("content")), pattern, '!'), cb.exists(tags)));
        if (categoryKey != null && !categoryKey.isBlank()) {
            if (categoryKey.length() > 40) throw invalid("话题分类无效");
            filters.add(cb.equal(post.get("categoryKey"), categoryKey.strip().toUpperCase(Locale.ROOT)));
        }
        // A time + ID cursor prevents missing posts published within the same millisecond.
        if (beforeTime != null) {
            filters.add(cb.or(cb.lessThan(post.get("createTime"), beforeTime),
                cb.and(cb.equal(post.get("createTime"), beforeTime),
                    cb.lessThan(post.get("postId"), beforeId))));
        }
        if (latitude != null) {
            // Haversine's a <= sin(radius / 2R)^2, evaluated in the database before pagination.
            // Coordinates use the app's existing AMap coordinate source; nearby radius is 10 km.
            double radians = Math.PI / 180;
            Expression<Double> latRadians = cb.prod(post.<Double>get("latitude"), radians);
            Expression<Double> deltaLat = cb.prod(cb.diff(post.<Double>get("latitude"), latitude), radians / 2);
            Expression<Double> deltaLon = cb.prod(cb.diff(post.<Double>get("longitude"), longitude), radians / 2);
            Expression<Double> sinLat = cb.function("sin", Double.class, deltaLat);
            Expression<Double> sinLon = cb.function("sin", Double.class, deltaLon);
            Expression<Double> a = cb.sum(cb.prod(sinLat, sinLat),
                cb.prod(cb.prod(cb.function("cos", Double.class, latRadians), Math.cos(latitude * radians)),
                    cb.prod(sinLon, sinLon)));
            filters.add(cb.isNotNull(post.get("latitude")));
            filters.add(cb.isNotNull(post.get("longitude")));
            filters.add(cb.le(a, Math.pow(Math.sin(10.0 / (2 * 6371.0088)), 2)));
        }
        query.select(post).where(filters.toArray(Predicate[]::new))
            .orderBy(cb.desc(post.get("createTime")), cb.desc(post.get("postId")));
        var fetched = entityManager.createQuery(query).setMaxResults(pageSize + 1).getResultList();
        boolean hasMore = fetched.size() > pageSize;
        var page = hasMore ? fetched.subList(0, pageSize) : fetched;
        var last = page.isEmpty() ? null : page.get(page.size() - 1);
        return new SearchPage(page.stream().map(assembler::toPostSummaryView).toList(), hasMore,
            hasMore ? last.createTime : null, hasMore ? last.postId : null);
    }

    private ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, 40091, message);
    }
}
