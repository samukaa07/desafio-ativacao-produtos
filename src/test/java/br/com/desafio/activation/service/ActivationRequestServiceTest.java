package br.com.desafio.activation.service;

import br.com.desafio.activation.api.dto.CreateActivationRequest;
import br.com.desafio.activation.domain.ActivationRequest;
import br.com.desafio.activation.integration.ActivationProvider;
import br.com.desafio.activation.integration.ProviderException;
import br.com.desafio.activation.repository.ActivationRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class ActivationRequestServiceTest {
    @Mock
    ActivationRequestRepository repository;
    @Mock
    ActivationProvider provider;
    ActivationRequestService service;

    @BeforeEach
    void setUp() {
        service = new ActivationRequestService(repository, provider);
    }

    @Test
    void createsAndCompletesWhenProviderSucceeds() {
        when(repository.existsByExternalId("ext-1")).thenReturn(false);
        when(repository.saveAndFlush(any())).thenAnswer(i -> {
            ActivationRequest r = i.getArgument(0);
            setIdNotNeeded(r);
            return r;
        });
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
        ActivationRequest result = service.create(new CreateActivationRequest("ext-1", "customer-1", "PROD-1"));
        assertEquals("ext-1", result.getExternalId());
        assertEquals("COMPLETED", result.getStatus().name());
        verify(provider).activate("ext-1", "customer-1", "PROD-1");
    }

    @Test
    void rejectsDuplicateExternalId() {
        when(repository.existsByExternalId("ext-1")).thenReturn(true);
        assertThrows(ActivationRequestService.DuplicateExternalIdException.class, () -> service.create(new CreateActivationRequest("ext-1", "c1", "p1")));
        verifyNoInteractions(provider);
    }

    @Test
    void marksFailedWhenProviderFails() {
        when(repository.existsByExternalId("ext-2")).thenReturn(false);
        when(repository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
        doThrow(new ProviderException("indisponível")).when(provider).activate("ext-2", "c1", "p1");
        ActivationRequest result = service.create(new CreateActivationRequest("ext-2", "c1", "p1"));
        assertEquals("FAILED", result.getStatus().name());
        assertEquals("indisponível", result.getFailureReason());
    }

    @Test
    void returnsNotFoundWhenMissing() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());
        assertThrows(ActivationRequestService.ActivationRequestNotFoundException.class, () -> service.get(id));
    }

    private void setIdNotNeeded(ActivationRequest r) {
    }
}
