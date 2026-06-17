package com.study.grabthisforme.persistence.repository;

import com.study.grabthisforme.persistence.entity.GoodsUiEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoodsUiRepository extends JpaRepository<GoodsUiEntity, Long> {

    List<GoodsUiEntity> findAllByGoodsIdIn(List<Long> goodsIds);
}
