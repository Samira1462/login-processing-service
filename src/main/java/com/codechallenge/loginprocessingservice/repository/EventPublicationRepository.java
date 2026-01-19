package com.codechallenge.loginprocessingservice.repository;

import com.codechallenge.loginprocessingservice.model.EventPublicationEntity;
import com.codechallenge.loginprocessingservice.model.PublicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.UUID;

public interface EventPublicationRepository extends JpaRepository<EventPublicationEntity, UUID> {
    List<EventPublicationEntity> findByStatusOrderByCreatedAtAsc(PublicationStatus status, Pageable pageable);
}
