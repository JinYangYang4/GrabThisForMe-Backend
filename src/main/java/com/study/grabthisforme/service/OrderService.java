package com.study.grabthisforme.service;
import com.study.grabthisforme.common.ApiException;
import com.study.grabthisforme.controller.OrderController.CreateOrderRequest;
import com.study.grabthisforme.persistence.entity.OrderEntity;
import com.study.grabthisforme.persistence.repository.OrderRepository;
import com.study.grabthisforme.persistence.repository.UserAccountRepository;
import com.study.grabthisforme.service.view.OrderView;
import jakarta.transaction.Transactional;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class OrderService {
    public static final int OPEN=0, ACCEPTED=1, COMPLETED=2, DELIVERED=3, CANCELLED=4;
    public static final String BUY_GOODS="BUY_GOODS", PICKUP_EXPRESS="PICKUP_EXPRESS";
    private final OrderRepository orders;
    private final UserAccountRepository accounts;
    private final UserService users;
    private final com.fasterxml.jackson.databind.ObjectMapper json;
    public OrderService(OrderRepository orders, UserAccountRepository accounts, UserService users, com.fasterxml.jackson.databind.ObjectMapper json) {
        this.orders=orders; this.accounts=accounts; this.users=users; this.json=json;
    }
    public List<OrderView> listOrders(long userId, String role) {
        if (!Set.of("all", "buyer", "sender").contains(role)) throw bad("Invalid order role");
        Map<String,OrderEntity> result=new LinkedHashMap<>();
        if (!role.equals("sender")) orders.findAllByBuyerIdOrderByStartTimeDesc(userId).forEach(e -> result.put(e.orderId,e));
        if (!role.equals("buyer")) orders.findAllBySenderIdOrderByStartTimeDesc(userId).forEach(e -> result.put(e.orderId,e));
        return result.values().stream().sorted(Comparator.comparing((OrderEntity e)->e.startTime).reversed())
            .map(e -> OrderView.from(e,true)).toList();
    }
    public List<OrderView> available(long userId, int page, int limit, String errandType) {
        if (page<0 || limit<1 || limit>100) throw bad("Invalid page size");
        String normalizedType=normalizeFilterType(errandType);
        return orders.findAvailable(userId,System.currentTimeMillis(),normalizedType,PageRequest.of(page,limit)).stream()
            .map(e -> OrderView.from(e,false)).toList();
    }
    public OrderView getOrder(long userId, String id) {
        OrderEntity e=orders.findById(id).orElseThrow(OrderService::missing);
        boolean participant=participant(e,userId);
        if (!participant && (e.orderStatus!=OPEN || e.endTime<=System.currentTimeMillis())) throw forbidden();
        return OrderView.from(e,participant);
    }
    @Transactional
    public OrderView createOrder(long userId, CreateOrderRequest r) {
        String recipientName = r.recipientName() == null ? "" : r.recipientName().trim();
        String recipientPhone = r.recipientPhone() == null ? "" : r.recipientPhone().trim();
        boolean hasContact = !recipientName.isEmpty() || !recipientPhone.isEmpty();
        if (hasContact && (recipientName.isEmpty() || recipientName.length() > 30
                || recipientName.chars().anyMatch(Character::isISOControl)
                || !recipientPhone.matches("\\+?[0-9]{7,15}"))) {
            throw bad("请填写有效的收件人姓名和联系电话");
        }
        accounts.findForUpdate(userId).orElseThrow(OrderService::forbidden);
        String id="ORD_"+UUID.nameUUIDFromBytes((userId+":"+r.clientOrderId()).getBytes(StandardCharsets.UTF_8));
        var existing=orders.findById(id);
        String requestHash=hashRequest(r);
        if (existing.isPresent()) {
            if (!Objects.equals(existing.get().requestHash,requestHash))
                throw conflict("The idempotency key was already used for a different request");
            return OrderView.from(existing.get(),true);
        }
        int quantity=r.quantity()==null?1:r.quantity();
        if (quantity<1 || quantity>99) throw bad("Quantity must be between 1 and 99");
        long now=System.currentTimeMillis();
        long start=r.startTime()==null || r.startTime()==0 ? now : r.startTime();
        long end=r.endTime()==null || r.endTime()==0 ? Math.max(now,start)+3600000 : r.endTime();
        if (end<=now || end<=start || end-start>7L*24*3600000) throw bad("Invalid service time window");
        if (!Double.isFinite(r.goodsPrice()) || r.goodsPrice()<0) throw bad("Invalid estimated price");
        var buyer=users.getUser(userId);
        OrderEntity e=new OrderEntity(); e.orderId=id; e.buyerId=userId; e.buyerName=buyer.name(); e.buyerAvatarUrl=buyer.headPic();
        e.requestHash=requestHash; e.quantity=quantity; e.goodsId=0L; e.goodsName=r.goodsName().trim(); e.goodsMessage=r.goodsMessage(); e.goodsPrice=r.goodsPrice(); e.goodsPic=r.goodsPic();
        e.shelfNumber=r.shelfNumber(); e.aimPosition=r.aimPosition().trim(); e.atPosition=r.atPosition();
        // Snapshot, not a mutable address-book reference: later edits must not change an order.
        e.recipientName=recipientName; e.recipientPhone=recipientPhone;
        e.errandType=normalizeCreateType(r.errandType(),e.goodsName);
        e.startTime=start; e.endTime=end; e.orderStatus=OPEN; e.isAccepted=false;
        orders.save(e); return OrderView.from(e,true);
    }
    @Transactional
    public OrderView acceptOrder(long userId,String id) {
        OrderEntity e=locked(id);
        if (Objects.equals(e.buyerId,userId)) throw forbidden();
        if (e.orderStatus==ACCEPTED && Objects.equals(e.senderId,userId)) return OrderView.from(e,true);
        if (e.orderStatus!=OPEN || e.senderId!=null || e.endTime<=System.currentTimeMillis()) throw conflict("Order is no longer available");
        var sender=users.getUser(userId); e.senderId=userId; e.senderName=sender.name(); e.senderAvatarUrl=sender.headPic();
        e.orderStatus=ACCEPTED; e.isAccepted=true; orders.save(e); return OrderView.from(e,true);
    }
    @Transactional
    public OrderView updateStatus(long userId,String id,int status) {
        OrderEntity e=locked(id);
        boolean buyer=Objects.equals(e.buyerId,userId), sender=Objects.equals(e.senderId,userId);
        if ((status==DELIVERED && !sender) || ((status==COMPLETED || status==CANCELLED) && !buyer)) throw forbidden();
        if (!Set.of(DELIVERED,COMPLETED,CANCELLED).contains(status)) throw bad("Unsupported order transition");
        if (e.orderStatus==status) return OrderView.from(e,true);
        boolean valid=(status==DELIVERED && e.orderStatus==ACCEPTED) || (status==COMPLETED && e.orderStatus==DELIVERED)
            || (status==CANCELLED && e.orderStatus==OPEN);
        if (!valid) throw conflict("Order status has changed; refresh before retrying");
        e.orderStatus=status; orders.save(e); return OrderView.from(e,true);
    }
    private String hashRequest(CreateOrderRequest request) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(json.writeValueAsBytes(request)));
        } catch (java.security.NoSuchAlgorithmException | com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("Cannot fingerprint order request",ex);
        }
    }
    private String normalizeFilterType(String value) {
        if (value==null || value.isBlank()) return null;
        if (!Set.of(BUY_GOODS,PICKUP_EXPRESS).contains(value)) throw bad("Invalid errand type");
        return value;
    }
    private String normalizeCreateType(String value,String goodsName) {
        if (value==null || value.isBlank()) return goodsName.startsWith("快递代取")?PICKUP_EXPRESS:BUY_GOODS;
        if (!Set.of(BUY_GOODS,PICKUP_EXPRESS).contains(value)) throw bad("Invalid errand type");
        return value;
    }
    private OrderEntity locked(String id) { return orders.findForUpdate(id).orElseThrow(OrderService::missing); }
    private boolean participant(OrderEntity e,long id) { return Objects.equals(e.buyerId,id)||Objects.equals(e.senderId,id); }
    private static ApiException missing() { return new ApiException(HttpStatus.NOT_FOUND,40431,"Order not found"); }
    private static ApiException forbidden() { return new ApiException(HttpStatus.FORBIDDEN,40331,"No permission for this order action"); }
    private static ApiException bad(String text) { return new ApiException(HttpStatus.BAD_REQUEST,40031,text); }
    private static ApiException conflict(String text) { return new ApiException(HttpStatus.CONFLICT,40931,text); }
}
