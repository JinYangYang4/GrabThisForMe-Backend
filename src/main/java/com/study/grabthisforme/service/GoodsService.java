package com.study.grabthisforme.service;

import com.study.grabthisforme.common.ApiException;
import com.study.grabthisforme.common.IdGenerator;
import com.study.grabthisforme.persistence.entity.GoodsBaseEntity;
import com.study.grabthisforme.persistence.entity.GoodsPriceEntity;
import com.study.grabthisforme.persistence.entity.GoodsStateEntity;
import com.study.grabthisforme.persistence.entity.GoodsUiEntity;
import com.study.grabthisforme.persistence.entity.SecondhandTradeEntity;
import com.study.grabthisforme.persistence.entity.StoreEntity;
import com.study.grabthisforme.persistence.entity.UserLikedGoodsEntity;
import com.study.grabthisforme.persistence.repository.GoodsBaseRepository;
import com.study.grabthisforme.persistence.repository.GoodsPriceRepository;
import com.study.grabthisforme.persistence.repository.GoodsStateRepository;
import com.study.grabthisforme.persistence.repository.GoodsUiRepository;
import com.study.grabthisforme.persistence.repository.SecondhandTradeRepository;
import com.study.grabthisforme.persistence.repository.StoreRepository;
import com.study.grabthisforme.persistence.repository.UserLikedGoodsRepository;
import com.study.grabthisforme.persistence.repository.StoreGoodsCategoryRepository;
import com.study.grabthisforme.persistence.repository.StoreGoodsCategoryItemRepository;
import com.study.grabthisforme.persistence.entity.StoreGoodsCategoryEntity;
import com.study.grabthisforme.persistence.entity.StoreGoodsCategoryItemEntity;
import com.study.grabthisforme.service.view.GoodsView;
import jakarta.transaction.Transactional;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class GoodsService {

    private final GoodsBaseRepository goodsBaseRepository;
    private final GoodsPriceRepository goodsPriceRepository;
    private final GoodsUiRepository goodsUiRepository;
    private final GoodsStateRepository goodsStateRepository;
    private final SecondhandTradeRepository secondhandTradeRepository;
    private final StoreRepository storeRepository;
    private final UserLikedGoodsRepository userLikedGoodsRepository;
    private final StoreGoodsCategoryRepository categoryRepository;
    private final StoreGoodsCategoryItemRepository categoryItemRepository;
    private final IdGenerator idGenerator;
    private final ViewAssembler viewAssembler;

    public GoodsService(
        GoodsBaseRepository goodsBaseRepository,
        GoodsPriceRepository goodsPriceRepository,
        GoodsUiRepository goodsUiRepository,
        GoodsStateRepository goodsStateRepository,
        SecondhandTradeRepository secondhandTradeRepository,
        StoreRepository storeRepository,
        UserLikedGoodsRepository userLikedGoodsRepository,
        StoreGoodsCategoryRepository categoryRepository,
        StoreGoodsCategoryItemRepository categoryItemRepository,
        IdGenerator idGenerator,
        ViewAssembler viewAssembler
    ) {
        this.goodsBaseRepository = goodsBaseRepository;
        this.goodsPriceRepository = goodsPriceRepository;
        this.goodsUiRepository = goodsUiRepository;
        this.goodsStateRepository = goodsStateRepository;
        this.secondhandTradeRepository = secondhandTradeRepository;
        this.storeRepository = storeRepository;
        this.userLikedGoodsRepository = userLikedGoodsRepository;
        this.categoryRepository = categoryRepository;
        this.categoryItemRepository = categoryItemRepository;
        this.idGenerator = idGenerator;
        this.viewAssembler = viewAssembler;
    }

    public List<GoodsView> listGoods(String keyword, Long storeId, boolean secondhandOnly) {
        List<GoodsBaseEntity> goods;
        if (storeId != null) {
            goods = goodsBaseRepository.findAllByStoreId(storeId);
        } else if (keyword != null && !keyword.isBlank()) {
            goods = goodsBaseRepository.findAllByNameContainingIgnoreCaseOrderByGoodsIdDesc(keyword.trim());
        } else {
            goods = goodsBaseRepository.findAll().stream()
                .sorted(Comparator.comparing(entity -> entity.goodsId, Comparator.reverseOrder()))
                .toList();
        }

        List<Long> goodsIds = goods.stream().map(entity -> entity.goodsId).toList();
        List<Long> secondhandIds = secondhandTradeRepository.findAllByGoodsIdIn(goodsIds).stream()
            .map(entity -> entity.goodsId)
            .toList();
        return viewAssembler.getGoodsViews(
            secondhandOnly ? secondhandIds : goodsIds
        ).values().stream().toList();
    }

    public GoodsView getGoods(long goodsId) {
        GoodsView goodsView = viewAssembler.getGoodsView(goodsId);
        if (goodsView == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, 40411, "Goods not found");
        }
        return goodsView;
    }

    @Transactional
    public GoodsView createGoods(
        long userId,
        Long storeId,
        String name,
        String message,
        String categoryKey,
        Double price,
        Double discountPrice,
        String discountTag,
        String pic,
        String tag,
        String unit,
        Integer stock,
        Boolean isHot,
        Boolean secondhand,
        Double originalPrice,
        String quality,
        String usedTime,
        Boolean negotiable
    ) {
        if (price == null || !Double.isFinite(price) || price <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, 40011, "Price must be greater than zero");
        }
        if (discountPrice != null && (!Double.isFinite(discountPrice) || discountPrice < 0 || discountPrice > price)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, 40012, "Discount price must be between zero and price");
        }
        if (stock != null && stock < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, 40013, "Stock cannot be negative");
        }
        if (storeId != null && storeId > 0) {
            StoreEntity store = storeRepository.findById(storeId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, 40421, "Store not found"));
            if (!store.ownerId.equals(userId)) {
                throw new ApiException(HttpStatus.FORBIDDEN, 40321, "Only store owner can create goods");
            }
        }
        long goodsId = idGenerator.nextLongId();
        goodsBaseRepository.save(new GoodsBaseEntity(goodsId, storeId == null ? 0L : storeId, name, message, categoryKey));
        goodsPriceRepository.save(new GoodsPriceEntity(goodsId, price, discountPrice == null ? 0.0 : discountPrice, discountTag == null ? "" : discountTag));
        goodsUiRepository.save(new GoodsUiEntity(goodsId, pic == null ? "" : pic, tag == null ? "" : tag, unit == null ? "" : unit));
        goodsStateRepository.save(new GoodsStateEntity(goodsId, 0L, stock == null ? 0 : stock, false, Boolean.TRUE.equals(isHot), 0, 0L));
        if (storeId != null && storeId > 0) {
            addToBaseCategory(storeId, goodsId, StoreService.CATEGORY_ALL);
            addToBaseCategory(storeId, goodsId, StoreService.CATEGORY_UNCLASSIFIED);
        }
        if (Boolean.TRUE.equals(secondhand)) {
            secondhandTradeRepository.save(new SecondhandTradeEntity(
                goodsId,
                userId,
                originalPrice == null ? price : originalPrice,
                quality == null ? "" : quality,
                usedTime,
                0,
                negotiable == null || negotiable
            ));
        }
        return getGoods(goodsId);
    }

    @Transactional
    public GoodsView updateSecondhandStatus(long userId, long goodsId, int status) {
        var trade=secondhandTradeRepository.findForUpdate(goodsId)
            .orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,40412,"Secondhand listing not found"));
        if (!trade.saleUserId.equals(userId)) throw new ApiException(HttpStatus.FORBIDDEN,40312,"Only the seller can manage this listing");
        if (!java.util.Set.of(0,2,3).contains(status)) throw new ApiException(HttpStatus.BAD_REQUEST,40014,"Invalid listing status");
        if (trade.tradeStatus==2 && status!=2) throw new ApiException(HttpStatus.CONFLICT,40915,"Sold listings cannot be relisted");
        trade.tradeStatus=status; secondhandTradeRepository.save(trade);
        var state=goodsStateRepository.findById(goodsId).orElseThrow();
        state.isSoldOut=status!=0; goodsStateRepository.save(state);
        return getGoods(goodsId);
    }

    private void addToBaseCategory(long storeId, long goodsId, String categoryName) {
        StoreGoodsCategoryEntity category = categoryRepository.findAllByStoreIdOrderBySortOrderAsc(storeId).stream()
            .filter(group -> categoryName.equals(group.category))
            .findFirst()
            .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, 40921, "Store base categories are missing"));
        int sortOrder = categoryItemRepository.findAllByGroupIdIn(List.of(category.groupId)).size();
        categoryItemRepository.save(new StoreGoodsCategoryItemEntity(category.groupId, goodsId, sortOrder));
    }

    @Transactional
    public boolean setGoodsLiked(long userId, long goodsId, boolean liked) {
        getGoods(goodsId);
        Optional<UserLikedGoodsEntity> existing = userLikedGoodsRepository.findByUserIdAndGoodsId(userId, goodsId);
        if (liked) {
            if (existing.isEmpty()) {
                userLikedGoodsRepository.save(new UserLikedGoodsEntity(userId, goodsId, System.currentTimeMillis()));
            }
            return true;
        }
        existing.ifPresent(userLikedGoodsRepository::delete);
        return false;
    }
}
