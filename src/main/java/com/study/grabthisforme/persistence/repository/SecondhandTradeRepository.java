package com.study.grabthisforme.persistence.repository;

import com.study.grabthisforme.persistence.entity.SecondhandTradeEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SecondhandTradeRepository extends JpaRepository<SecondhandTradeEntity, Long> {

    List<SecondhandTradeEntity> findAllByGoodsIdIn(List<Long> goodsIds);
}
