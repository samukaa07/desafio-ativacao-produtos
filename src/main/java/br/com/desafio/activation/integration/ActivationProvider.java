package br.com.desafio.activation.integration;

public interface ActivationProvider {
    void activate(String externalId, String customerId, String productCode);
}
