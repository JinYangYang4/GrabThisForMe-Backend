package com.study.grabthisforme.persistence.repository;

import com.study.grabthisforme.persistence.entity.ChatGroupEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatGroupRepository extends JpaRepository<ChatGroupEntity, Long> {
}
