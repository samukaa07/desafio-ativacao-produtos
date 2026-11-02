package br.com.desafio.activation;

import br.com.desafio.activation.integration.HttpActivationProvider;
import br.com.desafio.activation.integration.ProviderException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class HttpActivationProviderTest {
    private HttpServer server;
    private volatile int responseStatus;
    private volatile long responseDelayMs;
    private volatile String receivedIdempotencyKey;
    private HttpActivationProvider provider;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/provider/v1/activate", exchange -> {
            try {
                exchange.getRequestBody().readAllBytes();
                receivedIdempotencyKey = exchange.getRequestHeaders().getFirst("Idempotency-Key");
                if (responseDelayMs > 0)
                    Thread.sleep(responseDelayMs);
                exchange.sendResponseHeaders(responseStatus, -1);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } catch (IOException ignored) {
                // cliente já desistiu por timeout
            } finally {
                exchange.close();
            }
        });
        server.start();
        provider = new HttpActivationProvider(RestClient.builder(), "http://localhost:" + server.getAddress().getPort(),
                Duration.ofSeconds(1), Duration.ofMillis(300));
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void succeedsOn2xxAndSendsIdempotencyKey() {
        responseStatus = 204;
        assertDoesNotThrow(() -> provider.activate("ext-1", "c1", "p1"));
        assertEquals("ext-1", receivedIdempotencyKey);
    }

    @Test
    void reportsRejectionOn400() {
        responseStatus = 400;
        ProviderException ex = assertThrows(ProviderException.class, () -> provider.activate("ext-1", "c1", "p1"));
        assertTrue(ex.getMessage().contains("rejeitada"));
        assertTrue(ex.getMessage().contains("HTTP 400"));
    }

    @Test
    void reportsProviderErrorOn500() {
        responseStatus = 500;
        ProviderException ex = assertThrows(ProviderException.class, () -> provider.activate("ext-1", "c1", "p1"));
        assertTrue(ex.getMessage().contains("HTTP 500"));
    }

    @Test
    void reportsTimeoutWhenProviderIsSlow() {
        responseStatus = 204;
        responseDelayMs = 1_500;
        ProviderException ex = assertThrows(ProviderException.class, () -> provider.activate("ext-1", "c1", "p1"));
        assertTrue(ex.getMessage().contains("Tempo limite"));
    }
}
