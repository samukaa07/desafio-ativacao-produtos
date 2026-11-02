package br.com.desafio.activation.integration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;

@Component
public class HttpActivationProvider implements ActivationProvider {
    private final RestClient client;

    public HttpActivationProvider(RestClient.Builder builder,
                                  @Value("${provider.base-url:http://localhost:8080}") String baseUrl,
                                  @Value("${provider.connect-timeout:2s}") Duration connectTimeout,
                                  @Value("${provider.read-timeout:5s}") Duration readTimeout) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(connectTimeout).build());
        requestFactory.setReadTimeout(readTimeout);
        this.client = builder.baseUrl(baseUrl).requestFactory(requestFactory).build();
    }

    @Override
    public void activate(String externalId, String customerId, String productCode) {
        try {
            client.post().uri("/provider/v1/activate").contentType(MediaType.APPLICATION_JSON).header("Idempotency-Key", externalId).body(new ProviderPayload(externalId, customerId, productCode)).retrieve().toBodilessEntity();
        } catch (HttpClientErrorException.TooManyRequests ex) {
            throw new ProviderException("Provedor limitou as requisicoes (HTTP 429).");
        } catch (HttpClientErrorException ex) {
            throw new ProviderException("Ativacao rejeitada pelo provedor (HTTP " + ex.getStatusCode().value() + ").");
        } catch (HttpServerErrorException ex) {
            throw new ProviderException("Erro interno ou indisponibilidade do provedor (HTTP " + ex.getStatusCode().value() + ").");
        } catch (ResourceAccessException ex) {
            throw new ProviderException("Tempo limite excedido ou falha de conexao com o provedor.");
        } catch (RestClientException ex) {
            throw new ProviderException("Falha na comunicacao com o provedor de ativacao.");
        }
    }

    private record ProviderPayload(String externalId, String customerId, String productCode) {
    }
}
