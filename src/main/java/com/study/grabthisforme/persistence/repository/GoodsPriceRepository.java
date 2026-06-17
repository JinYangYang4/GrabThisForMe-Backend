package com.study.grabthisforme.persistence.repository;

import com.study.grabthisforme.persistence.entity.GoodsPriceEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoodsPriceRepository extends JpaRepository<GoodsPriceEntity, Long> {

    List<GoodsPriceEntity> findAllByGoodsIdIn(List<Long> goodsIds);
}
