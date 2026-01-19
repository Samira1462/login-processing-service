package com.codechallenge.loginprocessingservice.persistence.repository;

import com.codechallenge.loginprocessingservice.persistence.model.OutboxEventEntity;
import com.codechallenge.loginprocessingservice.persistence.model.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, UUID> {
    List<OutboxEventEntity> findByStatusOrderByCreatedAtAsc(OutboxStatus status, Pageable pageable);
}
