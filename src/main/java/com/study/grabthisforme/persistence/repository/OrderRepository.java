package com.study.grabthisforme.persistence.repository;

import com.study.grabthisforme.persistence.entity.OrderEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<OrderEntity, String> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select o from OrderEntity o where o.orderId = :id")
    java.util.Optional<OrderEntity> findForUpdate(@org.springframework.data.repository.query.Param("id") String id);

    @org.springframework.data.jpa.repository.Query("select o from OrderEntity o where o.orderStatus = 0 and o.buyerId <> :userId and o.endTime > :now and (" +
        ":errandType is null or o.errandType = :errandType or " +
        "(o.errandType is null and :errandType = 'PICKUP_EXPRESS' and o.goodsName like '快递代取%') or " +
        "(o.errandType is null and :errandType = 'BUY_GOODS' and (o.goodsName is null or o.goodsName not like '快递代取%'))) " +
        "order by o.startTime desc, o.orderId desc")
    List<OrderEntity> findAvailable(@org.springframework.data.repository.query.Param("userId") long userId,
        @org.springframework.data.repository.query.Param("now") long now,
        @org.springframework.data.repository.query.Param("errandType") String errandType,
        org.springframework.data.domain.Pageable page);

    List<OrderEntity> findAllByBuyerIdOrderByStartTimeDesc(Long buyerId);

    List<OrderEntity> findAllBySenderIdOrderByStartTimeDesc(Long senderId);
}
