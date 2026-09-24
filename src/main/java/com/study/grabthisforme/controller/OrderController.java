package com.study.grabthisforme.controller;

import com.study.grabthisforme.common.ApiResponse;
import com.study.grabthisforme.common.AuthContext;
import com.study.grabthisforme.service.OrderService;
import com.study.grabthisforme.service.PurchaseService;
import com.study.grabthisforme.service.view.PurchaseRecordView;
import com.study.grabthisforme.service.view.PurchaseRecordView.PurchaseResultView;
import com.study.grabthisforme.service.view.OrderView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final PurchaseService purchaseService;

    public OrderController(OrderService orderService, PurchaseService purchaseService) {
        this.orderService = orderService;
        this.purchaseService = purchaseService;
    }

    @PostMapping("/purchase")
    public ApiResponse<PurchaseResultView> purchase(@Valid @RequestBody PurchaseRequest request) {
        List<PurchaseService.PurchaseItem> items = request.items().stream()
            .map(item -> new PurchaseService.PurchaseItem(item.goodsId(), item.quantity()))
            .toList();
        return ApiResponse.success(
            purchaseService.purchase(
                AuthContext.requireUserId(), request.clientPurchaseId(), request.userCouponId(), items
            )
        );
    }

    @GetMapping("/purchases")
    public ApiResponse<List<PurchaseRecordView>> listPurchaseHistory() {
        return ApiResponse.success(purchaseService.listHistory(AuthContext.requireUserId()));
    }

    @GetMapping
    public ApiResponse<List<OrderView>> listOrders(@RequestParam(defaultValue = "all") String role) {
        return ApiResponse.success(orderService.listOrders(AuthContext.requireUserId(), role));
    }

    @GetMapping("/capabilities")
    public ApiResponse<OrderCapabilities> capabilities() {
        AuthContext.requireUserId();
        return ApiResponse.success(new OrderCapabilities(true));
    }

    public record OrderCapabilities(boolean recipientContacts) {}

    @GetMapping("/{orderId}")
    public ApiResponse<OrderView> getOrder(@PathVariable String orderId) {
        return ApiResponse.success(orderService.getOrder(AuthContext.requireUserId(), orderId));
    }

    @PostMapping
    public ApiResponse<OrderView> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        return ApiResponse.success(orderService.createOrder(AuthContext.requireUserId(), request));
    }

    @PatchMapping("/{orderId}/accept")
    public ApiResponse<OrderView> acceptOrder(@PathVariable String orderId) {
        return ApiResponse.success(orderService.acceptOrder(AuthContext.requireUserId(), orderId));
    }

    @PatchMapping("/{orderId}/status")
    public ApiResponse<OrderView> updateStatus(@PathVariable String orderId, @RequestBody UpdateStatusRequest request) {
        return ApiResponse.success(orderService.updateStatus(AuthContext.requireUserId(), orderId, request.status()));
    }


    @GetMapping("/available")
    public ApiResponse<List<OrderView>> available(@RequestParam(defaultValue="0") int page,
        @RequestParam(defaultValue="50") int limit,
        @RequestParam(required=false) String errandType) {
        return ApiResponse.success(orderService.available(AuthContext.requireUserId(),page,limit,errandType));
    }
    public record CreateOrderRequest(
        @NotBlank @jakarta.validation.constraints.Size(max=80) String clientOrderId,
        @NotBlank @jakarta.validation.constraints.Size(max=120) String goodsName,
        @jakarta.validation.constraints.Size(max=200) String goodsMessage,
        @NotNull @jakarta.validation.constraints.PositiveOrZero Double goodsPrice,
        @jakarta.validation.constraints.Size(max=255) String goodsPic,
        @jakarta.validation.constraints.Size(max=100) String shelfNumber,
        @NotBlank @jakarta.validation.constraints.Size(max=200) String aimPosition,
        @jakarta.validation.constraints.Size(max=200) String atPosition,
        Long startTime, Long endTime,
        @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(99) Integer quantity,
        @jakarta.validation.constraints.Size(max=32) String errandType,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        @jakarta.validation.constraints.Size(max=30) String recipientName,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        @jakarta.validation.constraints.Size(max=16) String recipientPhone
    ) {
        // Keep legacy clients/tests compatible. Omitted contact fields retain the old request fingerprint.
        public CreateOrderRequest(String clientOrderId, String goodsName, String goodsMessage,
                Double goodsPrice, String goodsPic, String shelfNumber, String aimPosition, String atPosition,
                Long startTime, Long endTime, Integer quantity, String errandType) {
            this(clientOrderId, goodsName, goodsMessage, goodsPrice, goodsPic, shelfNumber,
                aimPosition, atPosition, startTime, endTime, quantity, errandType, null, null);
        }
    }

    public record UpdateStatusRequest(int status) {
    }

    public record PurchaseRequest(
        @NotBlank(message = "clientPurchaseId is required") String clientPurchaseId,
        String userCouponId,
        @NotEmpty(message = "items is required") List<@Valid PurchaseItemRequest> items
    ) {
    }

    public record PurchaseItemRequest(
        @NotNull(message = "goodsId is required") Long goodsId,
        @NotNull(message = "quantity is required") @Positive(message = "quantity must be positive") Integer quantity
    ) {
    }
}
