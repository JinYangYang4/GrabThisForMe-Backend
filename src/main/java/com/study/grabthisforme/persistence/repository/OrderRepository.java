package com.study.grabthisforme.persistence.repository;

import com.study.grabthisforme.persistence.entity.OrderEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<OrderEntity, String> {

    List<OrderEntity> findAllByBuyerIdOrderByStartTimeDesc(Long buyerId);

    List<OrderEntity> findAllBySenderIdOrderByStartTimeDesc(Long senderId);
}
