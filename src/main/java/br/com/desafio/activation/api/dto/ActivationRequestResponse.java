package br.com.desafio.activation.api.dto;

import br.com.desafio.activation.domain.ActivationRequest;
import br.com.desafio.activation.domain.RequestStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ActivationRequestResponse(UUID id, String externalId, String customerId, String productCode,
                                        RequestStatus status, OffsetDateTime createdAt, OffsetDateTime updatedAt,
                                        String failureReason) {
    public static ActivationRequestResponse from(ActivationRequest r) {
        return new ActivationRequestResponse(r.getId(), r.getExternalId(), r.getCustomerId(), r.getProductCode(), r.getStatus(), r.getCreatedAt(), r.getUpdatedAt(), r.getFailureReason());
    }
}
