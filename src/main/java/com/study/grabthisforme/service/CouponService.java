package com.study.grabthisforme.service;

import com.study.grabthisforme.common.ApiException;
import com.study.grabthisforme.common.IdGenerator;
import com.study.grabthisforme.persistence.entity.CouponTemplateEntity;
import com.study.grabthisforme.persistence.entity.UserCouponEntity;
import com.study.grabthisforme.persistence.repository.CouponTemplateRepository;
import com.study.grabthisforme.persistence.repository.UserCouponRepository;
import com.study.grabthisforme.service.view.CouponView.CouponPurchaseView;
import com.study.grabthisforme.service.view.CouponView.TemplateView;
import com.study.grabthisforme.service.view.CouponView.UserCouponView;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class CouponService {
    public static final String STATUS_AVAILABLE = "AVAILABLE";
    public static final String STATUS_USED = "USED";
    public static final String STATUS_EXPIRED = "EXPIRED";

    private final CouponTemplateRepository templateRepository;
    private final UserCouponRepository userCouponRepository;
    private final IdGenerator idGenerator;

    public CouponService(
        CouponTemplateRepository templateRepository,
        UserCouponRepository userCouponRepository,
        IdGenerator idGenerator
    ) {
        this.templateRepository = templateRepository;
        this.userCouponRepository = userCouponRepository;
        this.idGenerator = idGenerator;
    }

    public List<TemplateView> listMarket(long userId) {
        return templateRepository.findAllByIsActiveTrueOrderByDiscountAmountDesc().stream()
            .map(template -> toTemplateView(template, userId))
            .toList();
    }

    @Transactional
    public List<UserCouponView> listMine(long userId) {
        long now = System.currentTimeMillis();
        List<UserCouponEntity> coupons = userCouponRepository.findAllByUserIdOrderByAcquiredAtDesc(userId);
        expireCoupons(coupons, now);
        return coupons.stream().map(coupon -> toUserCouponView(coupon, null, null, now)).toList();
    }

    @Transactional
    public List<UserCouponView> listApplicable(long userId, long storeId, double orderAmount) {
        if (!Double.isFinite(orderAmount) || orderAmount < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, 40051, "orderAmount is invalid");
        }
        long now = System.currentTimeMillis();
        List<UserCouponEntity> coupons = userCouponRepository
            .findAllByUserIdAndStatusOrderByValidUntilAsc(userId, STATUS_AVAILABLE);
        expireCoupons(coupons, now);
        return coupons.stream()
            .filter(coupon -> isApplicable(coupon, storeId, orderAmount, now))
            .map(coupon -> toUserCouponView(coupon, storeId, orderAmount, now))
            .sorted((left, right) -> Double.compare(right.discountAmount(), left.discountAmount()))
            .toList();
    }

    @Transactional
    public CouponPurchaseView buyCoupon(long userId, long templateId, String clientRequestId) {
        String requestId = clientRequestId == null ? "" : clientRequestId.trim();
        if (requestId.isBlank() || requestId.length() > 80) {
            throw new ApiException(HttpStatus.BAD_REQUEST, 40052, "clientRequestId is invalid");
        }
        UserCouponEntity existing = userCouponRepository
            .findByUserIdAndAcquisitionRequestId(userId, requestId)
            .orElse(null);
        if (existing != null) {
            return new CouponPurchaseView("PAID", existing.purchasePricePaid, toUserCouponView(existing, null, null, System.currentTimeMillis()));
        }

        CouponTemplateEntity template = templateRepository.findByIdForUpdate(templateId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, 40451, "Coupon template not found"));
        if (!Boolean.TRUE.equals(template.isActive)) {
            throw new ApiException(HttpStatus.CONFLICT, 40951, "Coupon is not on sale");
        }
        int stock = template.stock == null ? 0 : template.stock;
        if (stock <= 0) {
            throw new ApiException(HttpStatus.CONFLICT, 40952, "Coupon is sold out");
        }
        long purchasedCount = userCouponRepository.countByUserIdAndTemplateId(userId, templateId);
        int limit = template.perUserLimit == null ? 1 : template.perUserLimit;
        if (purchasedCount >= limit) {
            throw new ApiException(HttpStatus.CONFLICT, 40953, "Coupon purchase limit reached");
        }

        long now = System.currentTimeMillis();
        UserCouponEntity coupon = new UserCouponEntity();
        coupon.userCouponId = "UC_" + idGenerator.nextConversationId();
        coupon.templateId = templateId;
        coupon.userId = userId;
        coupon.acquisitionRequestId = requestId;
        coupon.purchasePricePaid = template.purchasePrice == null ? 0.0 : template.purchasePrice;
        coupon.acquiredAt = now;
        coupon.validFrom = now;
        coupon.validUntil = now + Math.max(1, template.validDays == null ? 1 : template.validDays) * 86_400_000L;
        coupon.status = STATUS_AVAILABLE;
        userCouponRepository.save(coupon);

        template.stock = stock - 1;
        template.soldCount = (template.soldCount == null ? 0L : template.soldCount) + 1;
        templateRepository.save(template);
        return new CouponPurchaseView("PAID", coupon.purchasePricePaid, toUserCouponView(coupon, null, null, now));
    }

    @Transactional
    public CouponApplication useCoupon(
        long userId,
        String userCouponId,
        long storeId,
        BigDecimal orderAmount,
        String purchaseId,
        long usedAt
    ) {
        if (userCouponId == null || userCouponId.isBlank()) {
            return new CouponApplication(null, BigDecimal.ZERO);
        }
        UserCouponEntity coupon = userCouponRepository.findByIdForUpdate(userCouponId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, 40452, "User coupon not found"));
        if (!Long.valueOf(userId).equals(coupon.userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, 40351, "Coupon does not belong to current user");
        }
        if (!isApplicable(coupon, storeId, orderAmount.doubleValue(), usedAt)) {
            throw new ApiException(HttpStatus.CONFLICT, 40954, "Coupon is unavailable for this purchase");
        }
        CouponTemplateEntity template = templateRepository.findById(coupon.templateId)
            .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, 40955, "Coupon template is missing"));
        BigDecimal discount = BigDecimal.valueOf(template.discountAmount == null ? 0.0 : template.discountAmount)
            .min(orderAmount)
            .max(BigDecimal.ZERO);
        coupon.status = STATUS_USED;
        coupon.usedPurchaseId = purchaseId;
        coupon.usedAt = usedAt;
        userCouponRepository.save(coupon);
        return new CouponApplication(coupon.userCouponId, discount);
    }

    private boolean isApplicable(UserCouponEntity coupon, long storeId, double orderAmount, long now) {
        if (!STATUS_AVAILABLE.equals(coupon.status) || coupon.validFrom > now || coupon.validUntil < now) {
            return false;
        }
        CouponTemplateEntity template = templateRepository.findById(coupon.templateId).orElse(null);
        if (template == null || !Boolean.TRUE.equals(template.isActive)) {
            return false;
        }
        boolean storeMatches = template.storeId == null || template.storeId == 0 || template.storeId == storeId;
        double minimum = template.minimumAmount == null ? 0.0 : template.minimumAmount;
        return storeMatches && orderAmount >= minimum;
    }

    private void expireCoupons(List<UserCouponEntity> coupons, long now) {
        coupons.stream()
            .filter(coupon -> STATUS_AVAILABLE.equals(coupon.status) && coupon.validUntil < now)
            .forEach(coupon -> {
                coupon.status = STATUS_EXPIRED;
                userCouponRepository.save(coupon);
            });
    }

    private TemplateView toTemplateView(CouponTemplateEntity template, long userId) {
        long purchased = userCouponRepository.countByUserIdAndTemplateId(userId, template.templateId);
        int limit = template.perUserLimit == null ? 1 : template.perUserLimit;
        boolean canPurchase = Boolean.TRUE.equals(template.isActive) && template.stock != null && template.stock > 0 && purchased < limit;
        return new TemplateView(
            template.templateId, template.title, template.description, template.discountAmount,
            template.minimumAmount, template.purchasePrice, template.validDays, template.storeId,
            template.stock, limit, purchased, canPurchase
        );
    }

    private UserCouponView toUserCouponView(UserCouponEntity coupon, Long storeId, Double amount, long now) {
        CouponTemplateEntity template = templateRepository.findById(coupon.templateId)
            .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, 40955, "Coupon template is missing"));
        boolean applicable = storeId != null && amount != null && isApplicable(coupon, storeId, amount, now);
        return new UserCouponView(
            coupon.userCouponId, coupon.templateId, template.title, template.description,
            template.discountAmount, template.minimumAmount, template.storeId,
            coupon.purchasePricePaid, coupon.acquiredAt, coupon.validFrom, coupon.validUntil,
            coupon.status, coupon.usedPurchaseId, applicable
        );
    }

    public record CouponApplication(String userCouponId, BigDecimal discountAmount) {
    }
}
