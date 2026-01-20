package com.codechallenge.loginprocessingservice.repository;

import com.codechallenge.loginprocessingservice.model.LoginTrackingResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LoginTrackingResultRepository extends JpaRepository<LoginTrackingResultEntity, UUID> {

    Optional<LoginTrackingResultEntity> findByMessageId(UUID messageId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        insert into login_processing.login_tracking_result
          (id, message_id, customer_id, username, client, event_timestamp, customer_ip, request_result, created_at)
        values
          (:id, :messageId, :customerId, :username, :client, :eventTimestamp, :customerIp, :requestResult, now())
        on conflict (message_id) do nothing
        """, nativeQuery = true)
    int insertIgnore(
            @Param("id") UUID id,
            @Param("messageId") UUID messageId,
            @Param("customerId") UUID customerId,
            @Param("username") String username,
            @Param("client") String client,
            @Param("eventTimestamp") Instant eventTimestamp,
            @Param("customerIp") String customerIp,
            @Param("requestResult") String requestResult
    );
}
