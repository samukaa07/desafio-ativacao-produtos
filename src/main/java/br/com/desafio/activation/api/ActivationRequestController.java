package br.com.desafio.activation.api;

import br.com.desafio.activation.api.dto.ActivationRequestResponse;
import br.com.desafio.activation.api.dto.CreateActivationRequest;
import br.com.desafio.activation.service.ActivationRequestService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/requests")
public class ActivationRequestController {
    private final ActivationRequestService service;

    public ActivationRequestController(ActivationRequestService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ActivationRequestResponse create(@Valid @RequestBody CreateActivationRequest input) {
        return ActivationRequestResponse.from(service.create(input));
    }

    @GetMapping("/{id}")
    public ActivationRequestResponse get(@PathVariable UUID id) {
        return ActivationRequestResponse.from(service.get(id));
    }
}
