package br.com.desafio.activation.repository;

import br.com.desafio.activation.domain.ActivationRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ActivationRequestRepository extends JpaRepository<ActivationRequest, UUID> {
    boolean existsByExternalId(String externalId);
}
