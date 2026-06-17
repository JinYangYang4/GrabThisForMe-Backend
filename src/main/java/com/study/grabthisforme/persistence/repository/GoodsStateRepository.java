package com.study.grabthisforme.persistence.repository;

import com.study.grabthisforme.persistence.entity.GoodsStateEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoodsStateRepository extends JpaRepository<GoodsStateEntity, Long> {

    List<GoodsStateEntity> findAllByGoodsIdIn(List<Long> goodsIds);
}
