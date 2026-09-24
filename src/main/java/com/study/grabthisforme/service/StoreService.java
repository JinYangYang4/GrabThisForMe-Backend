package com.study.grabthisforme.service;

import com.study.grabthisforme.common.ApiException;
import com.study.grabthisforme.common.IdGenerator;
import com.study.grabthisforme.persistence.entity.StoreEntity;
import com.study.grabthisforme.persistence.entity.StoreTagEntity;
import com.study.grabthisforme.persistence.entity.StoreGoodsCategoryEntity;
import com.study.grabthisforme.persistence.entity.StoreGoodsCategoryItemEntity;
import com.study.grabthisforme.persistence.entity.GoodsBaseEntity;
import com.study.grabthisforme.persistence.entity.UserLikedStoreEntity;
import com.study.grabthisforme.persistence.repository.StoreRepository;
import com.study.grabthisforme.persistence.repository.StoreTagRepository;
import com.study.grabthisforme.persistence.repository.UserLikedStoreRepository;
import com.study.grabthisforme.persistence.repository.StoreGoodsCategoryRepository;
import com.study.grabthisforme.persistence.repository.StoreGoodsCategoryItemRepository;
import com.study.grabthisforme.persistence.repository.GoodsBaseRepository;
import com.study.grabthisforme.service.view.StoreView;
import jakarta.transaction.Transactional;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class StoreService {

    public static final String CATEGORY_ALL = "全部";
    public static final String CATEGORY_UNCLASSIFIED = "未分类";

    private final StoreRepository storeRepository;
    private final StoreTagRepository storeTagRepository;
    private final UserLikedStoreRepository userLikedStoreRepository;
    private final StoreGoodsCategoryRepository categoryRepository;
    private final StoreGoodsCategoryItemRepository categoryItemRepository;
    private final GoodsBaseRepository goodsBaseRepository;
    private final IdGenerator idGenerator;
    private final ViewAssembler viewAssembler;

    public StoreService(
        StoreRepository storeRepository,
        StoreTagRepository storeTagRepository,
        UserLikedStoreRepository userLikedStoreRepository,
        StoreGoodsCategoryRepository categoryRepository,
        StoreGoodsCategoryItemRepository categoryItemRepository,
        GoodsBaseRepository goodsBaseRepository,
        IdGenerator idGenerator,
        ViewAssembler viewAssembler
    ) {
        this.storeRepository = storeRepository;
        this.storeTagRepository = storeTagRepository;
        this.userLikedStoreRepository = userLikedStoreRepository;
        this.categoryRepository = categoryRepository;
        this.categoryItemRepository = categoryItemRepository;
        this.goodsBaseRepository = goodsBaseRepository;
        this.idGenerator = idGenerator;
        this.viewAssembler = viewAssembler;
    }

    public List<StoreView> listStores(String keyword) {
        List<StoreEntity> stores = keyword == null || keyword.isBlank()
            ? storeRepository.findAll().stream().sorted(Comparator.comparing(entity -> entity.storeId, Comparator.reverseOrder())).toList()
            : storeRepository.findAllByNameContainingIgnoreCaseOrderByStoreIdDesc(keyword.trim());
        return stores.stream().map(store -> viewAssembler.getStoreView(store.storeId, true)).toList();
    }

    public StoreView getStore(long storeId) {
        StoreView storeView = viewAssembler.getStoreView(storeId, true);
        if (storeView == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, 40421, "Store not found");
        }
        return storeView;
    }

    public List<StoreView> listStoresByOwner(long userId) {
        return storeRepository.findAllByOwnerId(userId).stream()
            .sorted(Comparator.comparing(entity -> entity.storeId, Comparator.reverseOrder()))
            .map(store -> viewAssembler.getStoreView(store.storeId, true))
            .toList();
    }

    @Transactional
    public StoreView createStore(
        long userId,
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
        List<String> tags,
        List<String> categories
    ) {
        long storeId = idGenerator.nextLongId();
        StoreEntity entity = new StoreEntity(
            storeId,
            userId,
            name,
            type,
            address,
            latitude,
            longitude,
            phone,
            businessHours,
            minOrderAmount == null ? "0" : minOrderAmount,
            deliveryFee == null ? "0" : deliveryFee,
            isOpen == null || isOpen,
            pic,
            0.0f,
            0L
        );
        storeRepository.save(entity);
        if (tags != null) {
            int sortOrder = 0;
            for (String tag : tags.stream().filter(value -> value != null && !value.isBlank()).toList()) {
                storeTagRepository.save(new StoreTagEntity(storeId, tag, sortOrder++));
            }
        }
        rewriteCategories(storeId, normalizeCategories(categories), Map.of());
        return getStore(storeId);
    }

    @Transactional
    public StoreView updateStore(long userId,long storeId,com.study.grabthisforme.controller.StoreController.CreateStoreRequest r) {
        var e=storeRepository.findById(storeId).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,40421,"Store not found"));
        if (!e.ownerId.equals(userId)) throw new ApiException(HttpStatus.FORBIDDEN,40321,"Only the owner can edit this store");
        try {
            if (new java.math.BigDecimal(r.minOrderAmount()==null?"0":r.minOrderAmount()).signum()<0
                || new java.math.BigDecimal(r.deliveryFee()==null?"0":r.deliveryFee()).signum()<0) throw new NumberFormatException();
        } catch(NumberFormatException ex) { throw new ApiException(HttpStatus.BAD_REQUEST,40021,"Invalid store amounts"); }
        e.name=r.name(); e.type=r.type(); e.address=r.address(); e.phone=r.phone(); e.businessHours=r.businessHours();
        e.latitude=r.latitude(); e.longitude=r.longitude(); e.minOrderAmount=r.minOrderAmount()==null?"0":r.minOrderAmount();
        e.deliveryFee=r.deliveryFee()==null?"0":r.deliveryFee(); e.isOpen=Boolean.TRUE.equals(r.isOpen()); e.pic=r.pic();
        storeRepository.save(e);
        storeTagRepository.deleteAllByStoreId(storeId);
        storeTagRepository.flush();
        if (r.tags()!=null) {
            var tags=r.tags().stream().map(String::trim).filter(t -> !t.isEmpty()).distinct().toList();
            for (int index=0; index<tags.size(); index++) storeTagRepository.save(new StoreTagEntity(storeId,tags.get(index),index));
        }
        // Category membership is managed by its dedicated transactional endpoint.
        return getStore(storeId);
    }

    @Transactional
    public StoreView updateCategories(
        long userId,
        long storeId,
        List<String> categories,
        Map<String, String> renamedCategories
    ) {
        requireOwnedStore(userId, storeId);
        rewriteCategories(
            storeId,
            normalizeCategories(categories),
            renamedCategories == null ? Map.of() : renamedCategories
        );
        return getStore(storeId);
    }

    @Transactional
    public StoreView assignGoodsCategory(long userId, long storeId, long goodsId, String category) {
        requireOwnedStore(userId, storeId);
        GoodsBaseEntity goods = goodsBaseRepository.findById(goodsId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, 40411, "Goods not found"));
        if (!Long.valueOf(storeId).equals(goods.storeId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, 40022, "Goods does not belong to this store");
        }

        List<StoreGoodsCategoryEntity> groups = categoryRepository.findAllByStoreIdOrderBySortOrderAsc(storeId);
        Map<Long, StoreGoodsCategoryEntity> groupById = groups.stream()
            .collect(java.util.stream.Collectors.toMap(group -> group.groupId, group -> group));
        List<StoreGoodsCategoryItemEntity> goodsItems = categoryItemRepository.findAllByGoodsId(goodsId);
        String normalized = category == null ? "" : category.trim();

        if (normalized.isBlank() || CATEGORY_UNCLASSIFIED.equals(normalized)) {
            goodsItems.stream()
                .filter(item -> {
                    StoreGoodsCategoryEntity group = groupById.get(item.groupId);
                    return group != null && !CATEGORY_ALL.equals(group.category);
                })
                .forEach(categoryItemRepository::delete);
            StoreGoodsCategoryEntity unclassified = requireCategory(groups, CATEGORY_UNCLASSIFIED);
            saveCategoryItemIfAbsent(unclassified.groupId, goodsId);
        } else {
            if (CATEGORY_ALL.equals(normalized)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, 40023, "Cannot assign goods to the all category");
            }
            StoreGoodsCategoryEntity target = requireCategory(groups, normalized);
            goodsItems.stream()
                .filter(item -> {
                    StoreGoodsCategoryEntity group = groupById.get(item.groupId);
                    return group != null && CATEGORY_UNCLASSIFIED.equals(group.category);
                })
                .forEach(categoryItemRepository::delete);
            saveCategoryItemIfAbsent(target.groupId, goodsId);
        }
        saveCategoryItemIfAbsent(requireCategory(groups, CATEGORY_ALL).groupId, goodsId);
        return getStore(storeId);
    }

    private StoreEntity requireOwnedStore(long userId, long storeId) {
        StoreEntity store = storeRepository.findById(storeId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, 40421, "Store not found"));
        if (!Long.valueOf(userId).equals(store.ownerId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, 40321, "Only store owner can manage this store");
        }
        return store;
    }

    private List<String> normalizeCategories(List<String> categories) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        normalized.add(CATEGORY_ALL);
        normalized.add(CATEGORY_UNCLASSIFIED);
        if (categories != null) {
            categories.stream()
                .filter(java.util.Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .filter(value -> !CATEGORY_ALL.equals(value) && !CATEGORY_UNCLASSIFIED.equals(value))
                .forEach(normalized::add);
        }
        return new ArrayList<>(normalized);
    }

    private void rewriteCategories(long storeId, List<String> categories, Map<String, String> renamedCategories) {
        List<StoreGoodsCategoryEntity> oldGroups = categoryRepository.findAllByStoreIdOrderBySortOrderAsc(storeId);
        List<Long> oldGroupIds = oldGroups.stream().map(group -> group.groupId).toList();
        List<StoreGoodsCategoryItemEntity> oldItems = oldGroupIds.isEmpty()
            ? List.of()
            : categoryItemRepository.findAllByGroupIdIn(oldGroupIds);
        Map<Long, String> oldNameById = oldGroups.stream()
            .collect(java.util.stream.Collectors.toMap(group -> group.groupId, group -> group.category));
        Set<String> allowedCustom = categories.stream()
            .filter(name -> !CATEGORY_ALL.equals(name) && !CATEGORY_UNCLASSIFIED.equals(name))
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Map<Long, LinkedHashSet<String>> memberships = new LinkedHashMap<>();
        for (StoreGoodsCategoryItemEntity item : oldItems) {
            String oldName = oldNameById.get(item.groupId);
            if (oldName == null || CATEGORY_ALL.equals(oldName) || CATEGORY_UNCLASSIFIED.equals(oldName)) {
                continue;
            }
            String renamed = renamedCategories.getOrDefault(oldName, oldName);
            String normalized = renamed == null ? "" : renamed.trim();
            if (allowedCustom.contains(normalized)) {
                memberships.computeIfAbsent(item.goodsId, ignored -> new LinkedHashSet<>()).add(normalized);
            }
        }

        oldGroups.forEach(group -> categoryItemRepository.deleteAllByGroupId(group.groupId));
        categoryRepository.deleteAll(oldGroups);

        Map<String, StoreGoodsCategoryEntity> newGroups = new LinkedHashMap<>();
        for (int index = 0; index < categories.size(); index++) {
            StoreGoodsCategoryEntity group = new StoreGoodsCategoryEntity(
                idGenerator.nextLongId(), storeId, categories.get(index), index
            );
            categoryRepository.save(group);
            newGroups.put(group.category, group);
        }

        List<GoodsBaseEntity> goods = goodsBaseRepository.findAllByStoreId(storeId);
        for (GoodsBaseEntity item : goods) {
            saveCategoryItem(newGroups.get(CATEGORY_ALL).groupId, item.goodsId);
            Set<String> assigned = memberships.getOrDefault(item.goodsId, new LinkedHashSet<>());
            if (assigned.isEmpty()) {
                saveCategoryItem(newGroups.get(CATEGORY_UNCLASSIFIED).groupId, item.goodsId);
            } else {
                assigned.forEach(name -> saveCategoryItem(newGroups.get(name).groupId, item.goodsId));
            }
        }
    }

    private StoreGoodsCategoryEntity requireCategory(List<StoreGoodsCategoryEntity> groups, String category) {
        return groups.stream()
            .filter(group -> category.equals(group.category))
            .findFirst()
            .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, 40024, "Store category not found: " + category));
    }

    private void saveCategoryItemIfAbsent(long groupId, long goodsId) {
        String id = groupId + ":" + goodsId;
        if (!categoryItemRepository.existsById(id)) {
            saveCategoryItem(groupId, goodsId);
        }
    }

    private void saveCategoryItem(long groupId, long goodsId) {
        int sortOrder = categoryItemRepository.findAllByGroupIdIn(List.of(groupId)).size();
        categoryItemRepository.save(new StoreGoodsCategoryItemEntity(groupId, goodsId, sortOrder));
    }

    @Transactional
    public boolean setStoreLiked(long userId, long storeId, boolean liked) {
        getStore(storeId);
        Optional<UserLikedStoreEntity> existing = userLikedStoreRepository.findByUserIdAndStoreId(userId, storeId);
        if (liked) {
            if (existing.isEmpty()) {
                userLikedStoreRepository.save(new UserLikedStoreEntity(userId, storeId, System.currentTimeMillis()));
            }
            return true;
        }
        existing.ifPresent(userLikedStoreRepository::delete);
        return false;
    }
}
