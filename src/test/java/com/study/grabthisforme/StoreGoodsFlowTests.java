package com.study.grabthisforme;

import static org.assertj.core.api.Assertions.assertThat;

import com.study.grabthisforme.service.GoodsService;
import com.study.grabthisforme.service.StoreService;
import com.study.grabthisforme.service.PurchaseService;
import com.study.grabthisforme.service.CouponService;
import com.study.grabthisforme.persistence.repository.CouponTemplateRepository;
import com.study.grabthisforme.persistence.repository.UserCouponRepository;
import com.study.grabthisforme.common.ApiException;
import com.study.grabthisforme.service.view.GoodsView;
import com.study.grabthisforme.service.view.StoreView;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class StoreGoodsFlowTests {

    @Autowired
    private StoreService storeService;

    @Autowired
    private GoodsService goodsService;

    @Autowired
    private PurchaseService purchaseService;

    @Autowired
    private CouponService couponService;

    @Autowired
    private CouponTemplateRepository couponTemplateRepository;

    @Autowired
    private UserCouponRepository userCouponRepository;

    @Test
    void couponPurchaseIsIdempotentAndCheckoutRedeemsCoupon() {
        long ownerId = 11_001L;
        long buyerId = 10_002L;
        StoreView store = storeService.createStore(
            ownerId, "Coupon store", "Shop", "Test address", null, null, null, null,
            "0", "0", true, "", List.of(), List.of()
        );
        GoodsView goods = goodsService.createGoods(
            ownerId, store.id(), "Coupon goods", "Coupon checkout", "OTHER", 120.0, 120.0, "",
            "", "", "item", 10, false, false, null, null, null, null
        );

        int stockBefore = couponTemplateRepository.findById(5001L).orElseThrow().stock;
        var bought = couponService.buyCoupon(buyerId, 5001L, "coupon-buy-1");
        var repeatedBuy = couponService.buyCoupon(buyerId, 5001L, "coupon-buy-1");

        assertThat(repeatedBuy.coupon().userCouponId()).isEqualTo(bought.coupon().userCouponId());
        assertThat(couponTemplateRepository.findById(5001L).orElseThrow().stock).isEqualTo(stockBefore - 1);
        assertThat(userCouponRepository.countByUserIdAndTemplateId(buyerId, 5001L)).isEqualTo(1L);
        assertThat(couponService.listApplicable(buyerId, store.id(), 120.0))
            .extracting(coupon -> coupon.userCouponId())
            .containsExactly(bought.coupon().userCouponId());

        var result = purchaseService.purchase(
            buyerId,
            "coupon-checkout-1",
            bought.coupon().userCouponId(),
            List.of(new PurchaseService.PurchaseItem(goods.id(), 1))
        );

        assertThat(result.subtotalAmount()).isEqualTo(120.0);
        assertThat(result.discountAmount()).isEqualTo(20.0);
        assertThat(result.totalAmount()).isEqualTo(100.0);
        assertThat(result.userCouponId()).isEqualTo(bought.coupon().userCouponId());
        assertThat(result.records()).singleElement().satisfies(record -> {
            assertThat(record.subtotalAmount()).isEqualTo(120.0);
            assertThat(record.discountAmount()).isEqualTo(20.0);
            assertThat(record.totalAmount()).isEqualTo(100.0);
        });
        assertThat(userCouponRepository.findById(bought.coupon().userCouponId()).orElseThrow().status)
            .isEqualTo(CouponService.STATUS_USED);
        assertThat(couponService.listApplicable(buyerId, store.id(), 120.0)).isEmpty();
    }

    @Test
    void ownerCanCreateStorePublishGoodsAndManageCategories() {
        long ownerId = 9_001L;
        StoreView store = storeService.createStore(
            ownerId,
            "测试店铺",
            "便利店",
            "测试地址",
            null,
            null,
            "13800000000",
            "08:00 - 22:00",
            "10",
            "2",
            true,
            "",
            List.of("测试"),
            List.of("饮品")
        );

        assertThat(store.categories()).extracting(StoreView.StoreGoodsCategoryView::category)
            .containsExactly(StoreService.CATEGORY_ALL, StoreService.CATEGORY_UNCLASSIFIED, "饮品");

        GoodsView goods = goodsService.createGoods(
            ownerId,
            store.id(),
            "测试商品",
            "商品描述",
            "FOOD",
            12.5,
            10.0,
            "折扣",
            "",
            "新品",
            "份",
            20,
            false,
            false,
            null,
            null,
            null,
            null
        );

        StoreView afterPublish = storeService.getStore(store.id());
        assertThat(goodsIn(afterPublish, StoreService.CATEGORY_ALL)).containsExactly(goods.id());
        assertThat(goodsIn(afterPublish, StoreService.CATEGORY_UNCLASSIFIED)).containsExactly(goods.id());

        StoreView assigned = storeService.assignGoodsCategory(ownerId, store.id(), goods.id(), "饮品");
        assertThat(goodsIn(assigned, "饮品")).containsExactly(goods.id());
        assertThat(goodsIn(assigned, StoreService.CATEGORY_UNCLASSIFIED)).isEmpty();

        StoreView renamed = storeService.updateCategories(
            ownerId,
            store.id(),
            List.of("饮料"),
            Map.of("饮品", "饮料")
        );
        assertThat(goodsIn(renamed, "饮料")).containsExactly(goods.id());

        StoreView unclassified = storeService.assignGoodsCategory(ownerId, store.id(), goods.id(), null);
        assertThat(goodsIn(unclassified, StoreService.CATEGORY_UNCLASSIFIED)).containsExactly(goods.id());
        assertThat(goodsIn(unclassified, "饮料")).isEmpty();
    }

    @Test
    void purchaseIsAtomicUpdatesStockAndIsIdempotent() {
        long ownerId = 10_001L;
        long buyerId = 10_002L;
        StoreView store = storeService.createStore(
            ownerId, "购买测试店", "便利店", "测试地址", null, null, null, null,
            "0", "0", true, "", List.of(), List.of()
        );
        GoodsView goods = goodsService.createGoods(
            ownerId, store.id(), "限量商品", "库存测试", "OTHER", 20.0, 18.0, "优惠",
            "", "", "件", 5, false, false, null, null, null, null
        );

        var first = purchaseService.purchase(
            buyerId,
            "purchase-test-1",
            List.of(new PurchaseService.PurchaseItem(goods.id(), 2))
        );
        assertThat(first.totalAmount()).isEqualTo(36.0);
        assertThat(first.records()).singleElement().satisfies(record -> {
            assertThat(record.quantity()).isEqualTo(2);
            assertThat(record.unitPrice()).isEqualTo(18.0);
        });
        assertThat(goodsService.getGoods(goods.id()).state().stock()).isEqualTo(3);
        assertThat(goodsService.getGoods(goods.id()).state().soldCount()).isEqualTo(2L);

        var repeated = purchaseService.purchase(
            buyerId,
            "purchase-test-1",
            List.of(new PurchaseService.PurchaseItem(goods.id(), 2))
        );
        assertThat(repeated.purchaseId()).isEqualTo(first.purchaseId());
        assertThat(goodsService.getGoods(goods.id()).state().stock()).isEqualTo(3);
        assertThat(purchaseService.listHistory(buyerId)).hasSize(1);

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
            purchaseService.purchase(
                buyerId,
                "purchase-test-insufficient",
                List.of(new PurchaseService.PurchaseItem(goods.id(), 4))
            )
        ).isInstanceOf(ApiException.class).hasMessageContaining("Insufficient stock");
        assertThat(goodsService.getGoods(goods.id()).state().stock()).isEqualTo(3);
        assertThat(purchaseService.listHistory(buyerId)).hasSize(1);
    }

    private List<Long> goodsIn(StoreView store, String category) {
        return store.categories().stream()
            .filter(item -> category.equals(item.category()))
            .findFirst()
            .orElseThrow()
            .goods().stream()
            .map(GoodsView::id)
            .toList();
    }
}
