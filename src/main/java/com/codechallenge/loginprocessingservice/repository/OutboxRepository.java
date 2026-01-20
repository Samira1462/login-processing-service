package com.codechallenge.loginprocessingservice.repository;

import com.codechallenge.loginprocessingservice.model.OutboxEntity;
import com.codechallenge.loginprocessingservice.model.PublicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEntity, UUID> {
    List<OutboxEntity> findByStatusOrderByCreatedAtAsc(PublicationStatus status, Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        insert into login_processing.outbox_event
          (id, aggregate_type, aggregate_id, event_type, topic, key, payload, status, retry_count, last_error, last_attempt_at, sent_at, version, created_at)
        values
          (:id, :aggregateType, :aggregateId, :eventType, :topic, :key, :payload, 'NEW', 0, null, null, null, 0, now())
        on conflict (aggregate_type, aggregate_id, event_type) do nothing
        """, nativeQuery = true)
    int insertIgnore(
            @Param("id") UUID id,
            @Param("aggregateType") String aggregateType,
            @Param("aggregateId") UUID aggregateId,
            @Param("eventType") String eventType,
            @Param("topic") String topic,
            @Param("key") String key,
            @Param("payload") byte[] payload
    );
}
