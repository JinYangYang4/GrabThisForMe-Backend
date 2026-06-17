package com.study.grabthisforme.service;

import com.study.grabthisforme.common.ApiException;
import com.study.grabthisforme.common.IdGenerator;
import com.study.grabthisforme.persistence.entity.OrderEntity;
import com.study.grabthisforme.persistence.repository.OrderRepository;
import com.study.grabthisforme.service.view.GoodsView;
import com.study.grabthisforme.service.view.OrderView;
import com.study.grabthisforme.service.view.UserView;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final GoodsService goodsService;
    private final UserService userService;
    private final IdGenerator idGenerator;
    private final ViewAssembler viewAssembler;

    public OrderService(
        OrderRepository orderRepository,
        GoodsService goodsService,
        UserService userService,
        IdGenerator idGenerator,
        ViewAssembler viewAssembler
    ) {
        this.orderRepository = orderRepository;
        this.goodsService = goodsService;
        this.userService = userService;
        this.idGenerator = idGenerator;
        this.viewAssembler = viewAssembler;
    }

    public List<OrderView> listOrders(long userId, String role) {
        List<OrderEntity> result = new ArrayList<>();
        if ("buyer".equalsIgnoreCase(role)) {
            result.addAll(orderRepository.findAllByBuyerIdOrderByStartTimeDesc(userId));
        } else if ("sender".equalsIgnoreCase(role)) {
            result.addAll(orderRepository.findAllBySenderIdOrderByStartTimeDesc(userId));
        } else {
            result.addAll(orderRepository.findAllByBuyerIdOrderByStartTimeDesc(userId));
            result.addAll(orderRepository.findAllBySenderIdOrderByStartTimeDesc(userId));
        }
        return result.stream()
            .distinct()
            .sorted(Comparator.comparing(entity -> entity.startTime, Comparator.reverseOrder()))
            .map(viewAssembler::toOrderView)
            .toList();
    }

    public OrderView getOrder(String orderId) {
        OrderEntity entity = orderRepository.findById(orderId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, 40431, "Order not found"));
        return viewAssembler.toOrderView(entity);
    }

    @Transactional
    public OrderView createOrder(
        long userId,
        long goodsId,
        String shelfNumber,
        String aimPosition,
        String atPosition,
        Long startTime,
        Long endTime
    ) {
        UserView buyer = userService.getUser(userId);
        GoodsView goods = goodsService.getGoods(goodsId);
        OrderEntity entity = new OrderEntity();
        entity.orderId = idGenerator.nextOrderId();
        entity.senderId = null;
        entity.senderName = "";
        entity.senderAvatarUrl = "";
        entity.buyerId = buyer.id();
        entity.buyerName = buyer.name();
        entity.buyerAvatarUrl = buyer.headPic();
        entity.goodsId = goods.id();
        entity.goodsName = goods.name();
        entity.goodsMessage = goods.message();
        entity.goodsPrice = goods.price().discountPrice() != null && goods.price().discountPrice() > 0
            ? goods.price().discountPrice()
            : goods.price().price();
        entity.goodsPic = goods.ui().pic();
        entity.shelfNumber = shelfNumber;
        entity.aimPosition = aimPosition;
        entity.atPosition = atPosition;
        entity.startTime = startTime == null ? System.currentTimeMillis() : startTime;
        entity.endTime = endTime == null ? System.currentTimeMillis() + 3_600_000L : endTime;
        entity.orderStatus = 0;
        entity.isAccepted = false;
        orderRepository.save(entity);
        return viewAssembler.toOrderView(entity);
    }

    @Transactional
    public OrderView acceptOrder(long userId, String orderId) {
        OrderEntity entity = orderRepository.findById(orderId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, 40431, "Order not found"));
        if (entity.buyerId.equals(userId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, 40031, "Buyer cannot accept own order");
        }
        UserView sender = userService.getUser(userId);
        entity.senderId = sender.id();
        entity.senderName = sender.name();
        entity.senderAvatarUrl = sender.headPic();
        entity.isAccepted = true;
        entity.orderStatus = 1;
        orderRepository.save(entity);
        return viewAssembler.toOrderView(entity);
    }

    @Transactional
    public OrderView updateStatus(long userId, String orderId, int status) {
        OrderEntity entity = orderRepository.findById(orderId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, 40431, "Order not found"));
        if (!userIdEquals(entity.senderId, userId) && !entity.buyerId.equals(userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, 40331, "No permission to update order");
        }
        entity.orderStatus = status;
        if (status >= 1) {
            entity.isAccepted = true;
        }
        orderRepository.save(entity);
        return viewAssembler.toOrderView(entity);
    }

    private boolean userIdEquals(Long left, long right) {
        return left != null && left == right;
    }
}
