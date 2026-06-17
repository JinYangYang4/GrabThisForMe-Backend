package com.study.grabthisforme.service;

import com.study.grabthisforme.common.ApiException;
import com.study.grabthisforme.common.Jsons;
import com.study.grabthisforme.persistence.entity.GoodsBaseEntity;
import com.study.grabthisforme.persistence.entity.GoodsPriceEntity;
import com.study.grabthisforme.persistence.entity.GoodsStateEntity;
import com.study.grabthisforme.persistence.entity.GoodsUiEntity;
import com.study.grabthisforme.persistence.entity.PostEntity;
import com.study.grabthisforme.persistence.entity.PostStatsEntity;
import com.study.grabthisforme.persistence.entity.StoreEntity;
import com.study.grabthisforme.persistence.entity.StoreTagEntity;
import com.study.grabthisforme.persistence.entity.UserAccountEntity;
import com.study.grabthisforme.persistence.entity.UserLikedGoodsEntity;
import com.study.grabthisforme.persistence.entity.UserLikedPostEntity;
import com.study.grabthisforme.persistence.entity.UserLikedStoreEntity;
import com.study.grabthisforme.persistence.entity.UserPostEntity;
import com.study.grabthisforme.persistence.entity.UserProfileEntity;
import com.study.grabthisforme.persistence.repository.GoodsBaseRepository;
import com.study.grabthisforme.persistence.repository.GoodsPriceRepository;
import com.study.grabthisforme.persistence.repository.GoodsStateRepository;
import com.study.grabthisforme.persistence.repository.GoodsUiRepository;
import com.study.grabthisforme.persistence.repository.PostRepository;
import com.study.grabthisforme.persistence.repository.PostStatsRepository;
import com.study.grabthisforme.persistence.repository.StoreRepository;
import com.study.grabthisforme.persistence.repository.StoreTagRepository;
import com.study.grabthisforme.persistence.repository.UserLikedGoodsRepository;
import com.study.grabthisforme.persistence.repository.UserLikedPostRepository;
import com.study.grabthisforme.persistence.repository.UserLikedStoreRepository;
import com.study.grabthisforme.persistence.repository.UserPostRepository;
import com.study.grabthisforme.persistence.repository.UserAccountRepository;
import com.study.grabthisforme.persistence.repository.UserProfileRepository;
import com.study.grabthisforme.service.view.UserView;
import com.study.grabthisforme.service.view.UserGoodsSummaryView;
import com.study.grabthisforme.service.view.UserPostSummaryView;
import com.study.grabthisforme.service.view.UserStoreSummaryView;
import jakarta.transaction.Transactional;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserAccountRepository userAccountRepository;
    private final UserProfileRepository userProfileRepository;
    private final ViewAssembler viewAssembler;
    private final UserPostRepository userPostRepository;
    private final UserLikedPostRepository userLikedPostRepository;
    private final UserLikedStoreRepository userLikedStoreRepository;
    private final UserLikedGoodsRepository userLikedGoodsRepository;
    private final PostRepository postRepository;
    private final PostStatsRepository postStatsRepository;
    private final StoreRepository storeRepository;
    private final StoreTagRepository storeTagRepository;
    private final GoodsBaseRepository goodsBaseRepository;
    private final GoodsPriceRepository goodsPriceRepository;
    private final GoodsUiRepository goodsUiRepository;
    private final GoodsStateRepository goodsStateRepository;

    public UserService(
        UserAccountRepository userAccountRepository,
        UserProfileRepository userProfileRepository,
        ViewAssembler viewAssembler,
        UserPostRepository userPostRepository,
        UserLikedPostRepository userLikedPostRepository,
        UserLikedStoreRepository userLikedStoreRepository,
        UserLikedGoodsRepository userLikedGoodsRepository,
        PostRepository postRepository,
        PostStatsRepository postStatsRepository,
        StoreRepository storeRepository,
        StoreTagRepository storeTagRepository,
        GoodsBaseRepository goodsBaseRepository,
        GoodsPriceRepository goodsPriceRepository,
        GoodsUiRepository goodsUiRepository,
        GoodsStateRepository goodsStateRepository
    ) {
        this.userAccountRepository = userAccountRepository;
        this.userProfileRepository = userProfileRepository;
        this.viewAssembler = viewAssembler;
        this.userPostRepository = userPostRepository;
        this.userLikedPostRepository = userLikedPostRepository;
        this.userLikedStoreRepository = userLikedStoreRepository;
        this.userLikedGoodsRepository = userLikedGoodsRepository;
        this.postRepository = postRepository;
        this.postStatsRepository = postStatsRepository;
        this.storeRepository = storeRepository;
        this.storeTagRepository = storeTagRepository;
        this.goodsBaseRepository = goodsBaseRepository;
        this.goodsPriceRepository = goodsPriceRepository;
        this.goodsUiRepository = goodsUiRepository;
        this.goodsStateRepository = goodsStateRepository;
    }

    public List<UserView> listUsers(String keyword) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        return userAccountRepository.findAll().stream()
            .sorted(Comparator.comparing((UserAccountEntity entity) -> entity.createTime).reversed())
            .map(entity -> viewAssembler.getUserView(entity.userId))
            .filter(user -> user != null)
            .filter(user -> normalizedKeyword.isBlank()
                || String.valueOf(user.id()).contains(normalizedKeyword)
                || (user.accountName() != null && user.accountName().toLowerCase(Locale.ROOT).contains(normalizedKeyword))
                || (user.name() != null && user.name().toLowerCase(Locale.ROOT).contains(normalizedKeyword)))
            .toList();
    }

    public UserView getUser(long userId) {
        UserView userView = viewAssembler.getUserView(userId);
        if (userView == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, 40401, "User not found");
        }
        return userView;
    }

    @Transactional
    public UserView updateProfile(
        long userId,
        String name,
        String avatarUrl,
        String phone,
        String email,
        Integer gender,
        Boolean isVip,
        String signature
    ) {
        UserProfileEntity entity = userProfileRepository.findById(userId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, 40401, "User not found"));
        if (name != null) {
            entity.displayName = name;
        }
        if (avatarUrl != null) {
            entity.avatarUrl = avatarUrl;
        }
        if (phone != null) {
            entity.phone = phone;
        }
        if (email != null) {
            entity.email = email;
        }
        if (gender != null) {
            entity.gender = gender;
        }
        if (isVip != null) {
            entity.isVip = isVip;
        }
        if (signature != null) {
            entity.signature = signature;
        }
        userProfileRepository.save(entity);
        return getUser(userId);
    }

    public List<UserPostSummaryView> listUserPosts(long userId) {
        return userPostRepository.findAllByUserId(userId).stream()
            .map(userPost -> postRepository.findById(userPost.postId).map(post -> {
                PostStatsEntity stats = postStatsRepository.findById(post.postId).orElse(null);
                UserView author = viewAssembler.getUserView(userId);
                return new UserPostSummaryView(
                    post.postId,
                    post.content,
                    Jsons.readStringList(post.imagesJson),
                    post.createTime,
                    author == null ? userId : author.id(),
                    author == null ? null : author.name(),
                    author == null ? null : author.headPic(),
                    stats == null ? 0 : stats.likeCount,
                    stats == null ? 0 : stats.commentCount
                );
            }).orElse(null))
            .filter(java.util.Objects::nonNull)
            .toList();
    }

    public List<UserPostSummaryView> listLikedPosts(long userId) {
        return userLikedPostRepository.findAllByUserId(userId).stream()
            .map(entity -> postRepository.findById(entity.postId).map(post -> {
                PostStatsEntity stats = postStatsRepository.findById(post.postId).orElse(null);
                UserPostEntity postAuthor = userPostRepository.findByPostId(post.postId);
                UserView author = postAuthor == null ? null : viewAssembler.getUserView(postAuthor.userId);
                return new UserPostSummaryView(
                    post.postId,
                    post.content,
                    Jsons.readStringList(post.imagesJson),
                    post.createTime,
                    author == null ? 0L : author.id(),
                    author == null ? null : author.name(),
                    author == null ? null : author.headPic(),
                    stats == null ? 0 : stats.likeCount,
                    stats == null ? 0 : stats.commentCount
                );
            }).orElse(null))
            .filter(java.util.Objects::nonNull)
            .toList();
    }

    public List<UserStoreSummaryView> listLikedStores(long userId) {
        List<Long> storeIds = userLikedStoreRepository.findAllByUserId(userId).stream()
            .map(entity -> entity.storeId)
            .toList();
        Map<Long, List<String>> tagsByStoreId = new HashMap<>();
        for (StoreTagEntity tagEntity : storeTagRepository.findAllByStoreIdIn(storeIds)) {
            tagsByStoreId.computeIfAbsent(tagEntity.storeId, key -> new java.util.ArrayList<>()).add(tagEntity.tag);
        }
        return storeRepository.findAllById(storeIds).stream()
            .map(store -> new UserStoreSummaryView(
                new UserStoreSummaryView.IdentityView(store.storeId, store.name, store.type, store.ownerId),
                new UserStoreSummaryView.LocationView(store.address, store.latitude, store.longitude),
                new UserStoreSummaryView.CommercialInfoView(
                    store.phone,
                    store.businessHours,
                    new java.math.BigDecimal(store.minOrderAmount == null ? "0" : store.minOrderAmount),
                    new java.math.BigDecimal(store.deliveryFee == null ? "0" : store.deliveryFee),
                    store.isOpen,
                    store.pic,
                    store.rating,
                    tagsByStoreId.getOrDefault(store.storeId, java.util.List.of())
                ),
                new UserStoreSummaryView.StatisticsView(store.salesVolume)
            ))
            .toList();
    }

    public List<UserGoodsSummaryView> listLikedGoods(long userId) {
        List<Long> goodsIds = userLikedGoodsRepository.findAllByUserId(userId).stream()
            .map(entity -> entity.goodsId)
            .toList();
        Map<Long, GoodsPriceEntity> priceMap = goodsPriceRepository.findAllByGoodsIdIn(goodsIds).stream()
            .collect(Collectors.toMap(entity -> entity.goodsId, entity -> entity));
        Map<Long, GoodsUiEntity> uiMap = goodsUiRepository.findAllByGoodsIdIn(goodsIds).stream()
            .collect(Collectors.toMap(entity -> entity.goodsId, entity -> entity));
        Map<Long, GoodsStateEntity> stateMap = goodsStateRepository.findAllByGoodsIdIn(goodsIds).stream()
            .collect(Collectors.toMap(entity -> entity.goodsId, entity -> entity));
        return goodsBaseRepository.findAllById(goodsIds).stream()
            .map(base -> new UserGoodsSummaryView(
                new UserGoodsSummaryView.BaseView(base.goodsId, base.storeId, base.name, base.message, base.categoryKey),
                new UserGoodsSummaryView.PriceView(
                    Optional.ofNullable(priceMap.get(base.goodsId)).map(entity -> entity.price).orElse(0.0),
                    Optional.ofNullable(priceMap.get(base.goodsId)).map(entity -> entity.discountPrice).orElse(0.0),
                    Optional.ofNullable(priceMap.get(base.goodsId)).map(entity -> entity.discountTag).orElse("")
                ),
                new UserGoodsSummaryView.UiView(
                    Optional.ofNullable(uiMap.get(base.goodsId)).map(entity -> entity.pic).orElse(""),
                    Optional.ofNullable(uiMap.get(base.goodsId)).map(entity -> entity.tag).orElse(""),
                    Optional.ofNullable(uiMap.get(base.goodsId)).map(entity -> entity.unit).orElse("")
                ),
                new UserGoodsSummaryView.StateView(
                    Optional.ofNullable(stateMap.get(base.goodsId)).map(entity -> entity.saleNumber).orElse(0L),
                    Optional.ofNullable(stateMap.get(base.goodsId)).map(entity -> entity.stock).orElse(0),
                    Optional.ofNullable(stateMap.get(base.goodsId)).map(entity -> entity.isSoldOut).orElse(false),
                    Optional.ofNullable(stateMap.get(base.goodsId)).map(entity -> entity.isHot).orElse(false),
                    Optional.ofNullable(stateMap.get(base.goodsId)).map(entity -> entity.purchaseStatus).orElse(0),
                    Optional.ofNullable(stateMap.get(base.goodsId)).map(entity -> entity.soldCount).orElse(0L)
                )
            ))
            .toList();
    }
}
