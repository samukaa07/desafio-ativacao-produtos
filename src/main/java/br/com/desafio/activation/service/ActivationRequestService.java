package br.com.desafio.activation.service;

import br.com.desafio.activation.api.dto.CreateActivationRequest;
import br.com.desafio.activation.domain.ActivationRequest;
import br.com.desafio.activation.domain.RequestStatus;
import br.com.desafio.activation.integration.ActivationProvider;
import br.com.desafio.activation.integration.ProviderException;
import br.com.desafio.activation.repository.ActivationRequestRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ActivationRequestService {
    private final ActivationRequestRepository repository;
    private final ActivationProvider provider;

    public ActivationRequestService(ActivationRequestRepository repository, ActivationProvider provider) {
        this.repository = repository;
        this.provider = provider;
    }

    @Transactional
    public ActivationRequest create(CreateActivationRequest input) {
        if (repository.existsByExternalId(input.externalId()))
            throw new DuplicateExternalIdException(input.externalId());
        ActivationRequest request = new ActivationRequest(input.externalId().trim(), input.customerId().trim(), input.productCode().trim());
        try {
            request = repository.saveAndFlush(request);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateExternalIdException(input.externalId());
        }
        request.markProcessing();
        try {
            provider.activate(request.getExternalId(), request.getCustomerId(), request.getProductCode());
            request.complete();
        } catch (ProviderException ex) {
            request.fail(ex.getMessage());
        }
        return repository.save(request);
    }

    @Transactional(readOnly = true)
    public ActivationRequest get(UUID id) {
        return repository.findById(id).orElseThrow(() -> new ActivationRequestNotFoundException(id));
    }

    public static class DuplicateExternalIdException extends RuntimeException {
        public DuplicateExternalIdException(String id) {
            super("Já existe uma solicitação com externalId: " + id);
        }
    }

    public static class ActivationRequestNotFoundException extends RuntimeException {
        public ActivationRequestNotFoundException(UUID id) {
            super("Solicitação não encontrada: " + id);
        }
    }
}
