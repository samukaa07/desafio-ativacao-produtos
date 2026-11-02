package br.com.desafio.activation;

import br.com.desafio.activation.integration.ActivationProvider;
import br.com.desafio.activation.integration.ProviderException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.UUID;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ActivationApiApplicationTests {
    @Autowired
    MockMvc mvc;
    @MockBean
    ActivationProvider provider;

    @Test
    void createsAndCanConsultRequest() throws Exception {
        String response = mvc.perform(post("/api/v1/requests").contentType(MediaType.APPLICATION_JSON).content("{\"externalId\":\"it-100\",\"customerId\":\"customer-1\",\"productCode\":\"PROD-1\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("COMPLETED")).andReturn().getResponse().getContentAsString();
        String id = response.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
        mvc.perform(get("/api/v1/requests/" + id)).andExpect(status().isOk()).andExpect(jsonPath("$.externalId").value("it-100"));
    }

    @Test
    void rejectsDuplicateExternalId() throws Exception {
        String body = "{\"externalId\":\"it-duplicate\",\"customerId\":\"customer-1\",\"productCode\":\"PROD-1\"}";
        mvc.perform(post("/api/v1/requests").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated());
        mvc.perform(post("/api/v1/requests").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isConflict());
    }

    @Test
    void validatesRequiredFields() throws Exception {
        mvc.perform(post("/api/v1/requests").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void rejectsMalformedJson() throws Exception {
        mvc.perform(post("/api/v1/requests").contentType(MediaType.APPLICATION_JSON).content("{invalid"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.traceId").exists());
    }

    @Test
    void returnsNotFoundForUnknownId() throws Exception {
        mvc.perform(get("/api/v1/requests/" + UUID.randomUUID())).andExpect(status().isNotFound());
    }

    @Test
    void rejectsInvalidUuid() throws Exception {
        mvc.perform(get("/api/v1/requests/not-a-uuid")).andExpect(status().isBadRequest());
    }

    @Test
    void keepsRequestAsFailedWhenProviderFails() throws Exception {
        doThrow(new ProviderException("Erro interno ou indisponibilidade do provedor (HTTP 500).")).when(provider).activate("it-fail", "customer-1", "PROD-1");
        mvc.perform(post("/api/v1/requests").contentType(MediaType.APPLICATION_JSON).content("{\"externalId\":\"it-fail\",\"customerId\":\"customer-1\",\"productCode\":\"PROD-1\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureReason").value("Erro interno ou indisponibilidade do provedor (HTTP 500)."));
    }
}
