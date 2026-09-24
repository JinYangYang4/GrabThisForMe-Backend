package com.study.grabthisforme.persistence.repository;

import com.study.grabthisforme.persistence.entity.SecondhandTradeEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SecondhandTradeRepository extends JpaRepository<SecondhandTradeEntity, Long> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select t from SecondhandTradeEntity t where t.goodsId = :id")
    java.util.Optional<SecondhandTradeEntity> findForUpdate(@org.springframework.data.repository.query.Param("id") Long id);
    List<SecondhandTradeEntity> findAllByGoodsIdIn(List<Long> goodsIds);
}
