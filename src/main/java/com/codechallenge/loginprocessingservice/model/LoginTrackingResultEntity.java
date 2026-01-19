package com.codechallenge.loginprocessingservice.model;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(
        name = "login_tracking_result",
        indexes = {
                @Index(name = "ix_login_tracking_result_customer_id", columnList = "customer_id"),
                @Index(name = "ix_login_tracking_result_event_timestamp", columnList = "event_timestamp")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "ux_login_tracking_result_message_id", columnNames = "message_id")
        },
        schema = "login_processing"
)
public class LoginTrackingResultEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "message_id", nullable = false, unique = true)
    private UUID messageId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(nullable = false)
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Client client;

    @Column(name = "event_timestamp", nullable = false)
    private Instant eventTimestamp;

    @Column(name = "customer_ip", nullable = false, length = 45)
    private String customerIp;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_result", nullable = false, length = 16)
    private RequestResult requestResult;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getMessageId() {
        return messageId;
    }

    public void setMessageId(UUID messageId) {
        this.messageId = messageId;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public void setCustomerId(UUID customerId) {
        this.customerId = customerId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Client getClient() {
        return client;
    }

    public void setClient(Client client) {
        this.client = client;
    }

    public Instant getEventTimestamp() {
        return eventTimestamp;
    }

    public void setEventTimestamp(Instant eventTimestamp) {
        this.eventTimestamp = eventTimestamp;
    }

    public String getCustomerIp() {
        return customerIp;
    }

    public void setCustomerIp(String customerIp) {
        this.customerIp = customerIp;
    }

    public RequestResult getRequestResult() {
        return requestResult;
    }

    public void setRequestResult(RequestResult requestResult) {
        this.requestResult = requestResult;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LoginTrackingResultEntity that)) return false;
        return Objects.equals(messageId, that.messageId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(messageId);
    }

    @Override
    public String toString() {
        return "LoginTrackingResultEntity{" +
                "id=" + id +
                ", messageId=" + messageId +
                ", customerId=" + customerId +
                ", username='" + username + '\'' +
                ", client='" + client + '\'' +
                ", eventTimestamp=" + eventTimestamp +
                ", customerIp='" + customerIp + '\'' +
                ", requestResult='" + requestResult + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}
