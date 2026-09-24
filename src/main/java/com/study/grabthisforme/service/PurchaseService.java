package com.study.grabthisforme.service;

import com.study.grabthisforme.common.ApiException;
import com.study.grabthisforme.common.IdGenerator;
import com.study.grabthisforme.persistence.entity.GoodsBaseEntity;
import com.study.grabthisforme.persistence.entity.GoodsPriceEntity;
import com.study.grabthisforme.persistence.entity.GoodsStateEntity;
import com.study.grabthisforme.persistence.entity.GoodsUiEntity;
import com.study.grabthisforme.persistence.entity.PurchaseRecordEntity;
import com.study.grabthisforme.persistence.entity.StoreEntity;
import com.study.grabthisforme.persistence.repository.GoodsBaseRepository;
import com.study.grabthisforme.persistence.repository.GoodsPriceRepository;
import com.study.grabthisforme.persistence.repository.GoodsStateRepository;
import com.study.grabthisforme.persistence.repository.GoodsUiRepository;
import com.study.grabthisforme.persistence.repository.PurchaseRecordRepository;
import com.study.grabthisforme.persistence.repository.StoreRepository;
import com.study.grabthisforme.service.view.PurchaseRecordView;
import com.study.grabthisforme.service.view.PurchaseRecordView.PurchaseResultView;
import com.study.grabthisforme.service.view.UserView;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class PurchaseService {

    private final PurchaseRecordRepository purchaseRecordRepository;
    private final GoodsBaseRepository goodsBaseRepository;
    private final GoodsPriceRepository goodsPriceRepository;
    private final GoodsStateRepository goodsStateRepository;
    private final GoodsUiRepository goodsUiRepository;
    private final StoreRepository storeRepository;
    private final UserService userService;
    private final IdGenerator idGenerator;
    private final CouponService couponService;

    public PurchaseService(
        PurchaseRecordRepository purchaseRecordRepository,
        GoodsBaseRepository goodsBaseRepository,
        GoodsPriceRepository goodsPriceRepository,
        GoodsStateRepository goodsStateRepository,
        GoodsUiRepository goodsUiRepository,
        StoreRepository storeRepository,
        UserService userService,
        IdGenerator idGenerator,
        CouponService couponService
    ) {
        this.purchaseRecordRepository = purchaseRecordRepository;
        this.goodsBaseRepository = goodsBaseRepository;
        this.goodsPriceRepository = goodsPriceRepository;
        this.goodsStateRepository = goodsStateRepository;
        this.goodsUiRepository = goodsUiRepository;
        this.storeRepository = storeRepository;
        this.userService = userService;
        this.idGenerator = idGenerator;
        this.couponService = couponService;
    }

    public List<PurchaseRecordView> listHistory(long userId) {
        return purchaseRecordRepository.findAllByBuyerIdOrderByCreatedTimeDesc(userId).stream()
            .map(this::toView)
            .toList();
    }

    @Transactional
    public PurchaseResultView purchase(long userId, String clientPurchaseId, List<PurchaseItem> requestedItems) {
        return purchase(userId, clientPurchaseId, null, requestedItems);
    }

    @Transactional
    public PurchaseResultView purchase(
        long userId,
        String clientPurchaseId,
        String userCouponId,
        List<PurchaseItem> requestedItems
    ) {
        String normalizedClientId = clientPurchaseId == null ? "" : clientPurchaseId.trim();
        if (normalizedClientId.isBlank() || normalizedClientId.length() > 80) {
            throw new ApiException(HttpStatus.BAD_REQUEST, 40041, "clientPurchaseId is invalid");
        }
        List<PurchaseRecordEntity> existing = purchaseRecordRepository
            .findAllByBuyerIdAndClientPurchaseIdOrderByRecordIdAsc(userId, normalizedClientId);
        if (!existing.isEmpty()) {
            return toResult(existing);
        }
        if (requestedItems == null || requestedItems.isEmpty() || requestedItems.size() > 50) {
            throw new ApiException(HttpStatus.BAD_REQUEST, 40042, "Purchase items must contain 1 to 50 products");
        }

        Map<Long, Integer> quantities = new TreeMap<>();
        for (PurchaseItem item : requestedItems) {
            if (item == null || item.goodsId() == null || item.goodsId() <= 0 || item.quantity() == null || item.quantity() <= 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST, 40043, "Goods id and positive quantity are required");
            }
            quantities.merge(item.goodsId(), item.quantity(), Math::addExact);
        }

        UserView buyer = userService.getUser(userId);
        long createdTime = System.currentTimeMillis();
        String purchaseId = idGenerator.nextOrderId().replace("ORD_", "BUY_");
        Long expectedStoreId = null;
        List<PurchaseRecordEntity> records = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;

        for (Map.Entry<Long, Integer> request : quantities.entrySet()) {
            long goodsId = request.getKey();
            int quantity = request.getValue();
            GoodsBaseEntity goods = goodsBaseRepository.findById(goodsId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, 40411, "Goods not found: " + goodsId));
            if (goods.storeId == null || goods.storeId <= 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST, 40044, "Only store goods can be purchased here");
            }
            if (expectedStoreId == null) {
                expectedStoreId = goods.storeId;
            } else if (!expectedStoreId.equals(goods.storeId)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, 40045, "All goods in one purchase must belong to the same store");
            }

            GoodsStateEntity state = goodsStateRepository.findByGoodsIdForUpdate(goodsId)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, 40911, "Goods stock state is missing"));
            int currentStock = state.stock == null ? 0 : state.stock;
            if (Boolean.TRUE.equals(state.isSoldOut) || currentStock < quantity) {
                throw new ApiException(HttpStatus.CONFLICT, 40912, "Insufficient stock for goods: " + goods.name);
            }

            GoodsPriceEntity price = goodsPriceRepository.findById(goodsId)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, 40913, "Goods price is missing"));
            double unitPrice = price.discountPrice != null && price.discountPrice > 0
                ? price.discountPrice
                : price.price;
            if (!Double.isFinite(unitPrice) || unitPrice <= 0) {
                throw new ApiException(HttpStatus.CONFLICT, 40914, "Goods price is invalid");
            }
            BigDecimal lineTotal = BigDecimal.valueOf(unitPrice).multiply(BigDecimal.valueOf(quantity));
            GoodsUiEntity ui = goodsUiRepository.findById(goodsId).orElse(null);
            StoreEntity store = storeRepository.findById(goods.storeId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, 40421, "Store not found"));

            state.stock = currentStock - quantity;
            state.saleNumber = (state.saleNumber == null ? 0L : state.saleNumber) + quantity;
            state.soldCount = (state.soldCount == null ? 0L : state.soldCount) + quantity;
            state.isSoldOut = state.stock == 0;
            goodsStateRepository.save(state);

            PurchaseRecordEntity record = new PurchaseRecordEntity();
            record.recordId = purchaseId + ":" + goodsId;
            record.purchaseId = purchaseId;
            record.clientPurchaseId = normalizedClientId;
            record.buyerId = buyer.id();
            record.buyerName = buyer.name();
            record.buyerAvatarUrl = buyer.headPic();
            record.storeId = store.storeId;
            record.storeName = store.name;
            record.goodsId = goods.goodsId;
            record.goodsName = goods.name;
            record.goodsMessage = goods.message;
            record.goodsPic = ui == null ? "" : ui.pic;
            record.quantity = quantity;
            record.unitPrice = unitPrice;
            record.subtotalAmount = lineTotal.doubleValue();
            record.discountAmount = 0.0;
            record.totalAmount = lineTotal.doubleValue();
            record.userCouponId = null;
            record.createdTime = createdTime;
            record.status = "PAID";
            records.add(record);
            subtotal = subtotal.add(lineTotal);
        }

        CouponService.CouponApplication coupon = couponService.useCoupon(
            userId, userCouponId, expectedStoreId, subtotal, purchaseId, createdTime
        );
        allocateDiscount(records, subtotal, coupon.discountAmount(), coupon.userCouponId());
        purchaseRecordRepository.saveAll(records);
        return toResult(records);
    }

    private void allocateDiscount(
        List<PurchaseRecordEntity> records,
        BigDecimal subtotal,
        BigDecimal discount,
        String userCouponId
    ) {
        BigDecimal normalizedDiscount = discount.min(subtotal).max(BigDecimal.ZERO)
            .setScale(2, RoundingMode.HALF_UP);
        BigDecimal allocated = BigDecimal.ZERO;
        for (int index = 0; index < records.size(); index++) {
            PurchaseRecordEntity record = records.get(index);
            BigDecimal lineSubtotal = BigDecimal.valueOf(record.subtotalAmount);
            BigDecimal lineDiscount;
            if (index == records.size() - 1) {
                lineDiscount = normalizedDiscount.subtract(allocated);
            } else {
                lineDiscount = normalizedDiscount.multiply(lineSubtotal)
                    .divide(subtotal, 2, RoundingMode.HALF_UP);
            }
            BigDecimal remaining = normalizedDiscount.subtract(allocated).max(BigDecimal.ZERO);
            lineDiscount = lineDiscount.min(remaining).min(lineSubtotal).max(BigDecimal.ZERO);
            allocated = allocated.add(lineDiscount);
            record.discountAmount = lineDiscount.doubleValue();
            record.totalAmount = lineSubtotal.subtract(lineDiscount).max(BigDecimal.ZERO).doubleValue();
            record.userCouponId = userCouponId;
        }
    }

    private PurchaseResultView toResult(List<PurchaseRecordEntity> records) {
        double subtotal = records.stream().map(this::subtotalOf)
            .reduce(BigDecimal.ZERO, BigDecimal::add).doubleValue();
        double discount = records.stream().map(this::discountOf)
            .reduce(BigDecimal.ZERO, BigDecimal::add).doubleValue();
        double total = records.stream().map(record -> BigDecimal.valueOf(record.totalAmount))
            .reduce(BigDecimal.ZERO, BigDecimal::add).doubleValue();
        PurchaseRecordEntity first = records.get(0);
        return new PurchaseResultView(
            first.purchaseId,
            subtotal,
            discount,
            total,
            first.userCouponId,
            first.createdTime,
            records.stream().map(this::toView).toList()
        );
    }

    private BigDecimal subtotalOf(PurchaseRecordEntity record) {
        return BigDecimal.valueOf(record.subtotalAmount == null ? record.totalAmount : record.subtotalAmount);
    }

    private BigDecimal discountOf(PurchaseRecordEntity record) {
        return BigDecimal.valueOf(record.discountAmount == null ? 0.0 : record.discountAmount);
    }

    private PurchaseRecordView toView(PurchaseRecordEntity record) {
        return new PurchaseRecordView(
            record.recordId,
            record.purchaseId,
            record.buyerId,
            record.buyerName,
            record.buyerAvatarUrl,
            record.storeId,
            record.storeName,
            record.goodsId,
            record.goodsName,
            record.goodsMessage,
            record.goodsPic,
            record.quantity,
            record.unitPrice,
            subtotalOf(record).doubleValue(),
            discountOf(record).doubleValue(),
            record.totalAmount,
            record.userCouponId,
            record.createdTime,
            record.status
        );
    }

    public record PurchaseItem(Long goodsId, Integer quantity) {
    }
}
