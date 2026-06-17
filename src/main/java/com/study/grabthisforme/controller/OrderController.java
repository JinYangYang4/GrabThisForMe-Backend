package com.study.grabthisforme.controller;

import com.study.grabthisforme.common.ApiResponse;
import com.study.grabthisforme.common.AuthContext;
import com.study.grabthisforme.service.OrderService;
import com.study.grabthisforme.service.view.OrderView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
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

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public ApiResponse<List<OrderView>> listOrders(@RequestParam(defaultValue = "all") String role) {
        return ApiResponse.success(orderService.listOrders(AuthContext.requireUserId(), role));
    }

    @GetMapping("/{orderId}")
    public ApiResponse<OrderView> getOrder(@PathVariable String orderId) {
        return ApiResponse.success(orderService.getOrder(orderId));
    }

    @PostMapping
    public ApiResponse<OrderView> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        return ApiResponse.success(orderService.createOrder(
            AuthContext.requireUserId(),
            request.goodsId(),
            request.shelfNumber(),
            request.aimPosition(),
            request.atPosition(),
            request.startTime(),
            request.endTime()
        ));
    }

    @PatchMapping("/{orderId}/accept")
    public ApiResponse<OrderView> acceptOrder(@PathVariable String orderId) {
        return ApiResponse.success(orderService.acceptOrder(AuthContext.requireUserId(), orderId));
    }

    @PatchMapping("/{orderId}/status")
    public ApiResponse<OrderView> updateStatus(@PathVariable String orderId, @RequestBody UpdateStatusRequest request) {
        return ApiResponse.success(orderService.updateStatus(AuthContext.requireUserId(), orderId, request.status()));
    }

    public record CreateOrderRequest(
        @NotNull(message = "goodsId is required") Long goodsId,
        String shelfNumber,
        String aimPosition,
        String atPosition,
        Long startTime,
        Long endTime
    ) {
    }

    public record UpdateStatusRequest(int status) {
    }
}
