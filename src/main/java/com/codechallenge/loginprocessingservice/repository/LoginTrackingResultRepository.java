package com.codechallenge.loginprocessingservice.repository;

import com.codechallenge.loginprocessingservice.model.LoginTrackingResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface LoginTrackingResultRepository extends JpaRepository<LoginTrackingResultEntity, UUID> {

    Optional<LoginTrackingResultEntity> findByMessageId(UUID messageId);
}
