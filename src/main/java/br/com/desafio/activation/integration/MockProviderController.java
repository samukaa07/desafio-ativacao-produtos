package br.com.desafio.activation.integration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * Provedor local simulado para demonstrar sucesso e falha sem depender de um terceiro real.
 */
@RestController
@RequestMapping("/provider/v1")
public class MockProviderController {
    @PostMapping("/activate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void activate(@Valid @RequestBody ProviderRequest request) {
        String code = request.productCode().toUpperCase();
        if (code.contains("FAIL") || code.contains("ERROR"))
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Falha simulada no provedor");
        if (code.contains("REJECT"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ativacao rejeitada pelo provedor");
    }

    public record ProviderRequest(@NotBlank @Size(max = 100) String externalId,
                                  @NotBlank @Size(max = 100) String customerId,
                                  @NotBlank @Size(max = 100) String productCode) {
    }
}
