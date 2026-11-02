package br.com.desafio.activation.domain;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "activation_requests", uniqueConstraints = @UniqueConstraint(name = "uk_activation_external_id", columnNames = "external_id"))
public class ActivationRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "external_id", nullable = false, length = 100, updatable = false)
    private String externalId;
    @Column(name = "customer_id", nullable = false, length = 100)
    private String customerId;
    @Column(name = "product_code", nullable = false, length = 100)
    private String productCode;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RequestStatus status;
    @Column(name = "failure_reason", length = 500)
    private String failureReason;
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected ActivationRequest() {
    }

    public ActivationRequest(String externalId, String customerId, String productCode) {
        this.externalId = externalId;
        this.customerId = customerId;
        this.productCode = productCode;
        this.status = RequestStatus.PENDING;
        this.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        this.updatedAt = this.createdAt;
    }

    public void markProcessing() {
        this.status = RequestStatus.PROCESSING;
        touch();
    }

    public void complete() {
        this.status = RequestStatus.COMPLETED;
        this.failureReason = null;
        touch();
    }

    public void fail(String reason) {
        this.status = RequestStatus.FAILED;
        this.failureReason = reason;
        touch();
    }

    private void touch() {
        this.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public UUID getId() {
        return id;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getProductCode() {
        return productCode;
    }

    public RequestStatus getStatus() {
        return status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
