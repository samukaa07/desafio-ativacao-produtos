# Activation API — Desafio Java 

API REST para registrar solicitações de ativação de produtos, consultar status e integrar com um provedor HTTP simulado. Projeto backend-only; não possui frontend, conforme o escopo interpretado do desafio.

## Stack
- Java 21, Spring Boot 3.4.5, Spring MVC, Bean Validation
- Spring Data JPA / Hibernate, PostgreSQL 16
- Flyway, Swagger/OpenAPI, Actuator
- JUnit 5, Mockito, Maven, Docker Compose
- GitHub Actions para build e testes

## Executar com Docker
Requisitos: Docker Desktop e Docker Compose.

```bash
docker compose up --build
```

A primeira inicialização pode levar alguns minutos. Endereços:
- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs
- Health: http://localhost:8080/actuator/health

Parar os containers: `docker compose down`. Apagar também os dados: `docker compose down -v`.

## Executar localmente
É necessário Java 21, Maven e PostgreSQL disponível com banco `activation_db`, usuário `activation`, senha `activation`.

```bash
mvn clean test
mvn spring-boot:run
```

Variáveis configuráveis: `DB_URL`, `DB_USER`, `DB_PASSWORD`, `SERVER_PORT`, `PROVIDER_BASE_URL`, `PROVIDER_CONNECT_TIMEOUT` (padrão `2s`), `PROVIDER_READ_TIMEOUT` (padrão `5s`).

## Endpoints
### Criar solicitação
`POST /api/v1/requests`

```json
{
  "externalId": "pedido-1001",
  "customerId": "cliente-42",
  "productCode": "PRODUTO-A"
}
```

Retorna `201 Created` com `id`, os campos enviados, `status`, `createdAt`, `updatedAt` e `failureReason` quando houver falha de ativação. O processamento do provedor é síncrono nesta implementação simplificada.

### Consultar solicitação
`GET /api/v1/requests/{id}` — informe o UUID retornado pelo POST.

### Status
- `PENDING`: solicitação registrada aguardando processamento.
- `PROCESSING`: chamada ao provedor em andamento.
- `COMPLETED`: provedor respondeu com sucesso.
- `FAILED`: provedor respondeu com erro ou falha de comunicação.

O fluxo atual processa de forma síncrona; `PENDING` e `PROCESSING` são estados transitórios internos e podem não ser observáveis na resposta normal do POST.

## Simulador do provedor
O endpoint local `POST /provider/v1/activate` simula o serviço externo e não é destinado a produção.
- `productCode` normal (ex.: `PRODUTO-A`): sucesso (`204`).
- `productCode` contendo `FAIL` ou `ERROR`: erro HTTP 500.
- `productCode` contendo `REJECT`: rejeição HTTP 400.

O cliente envia `Idempotency-Key` com o `externalId`. A simulação é local para que a integração possa ser demonstrada sem credenciais de terceiros.

## Tratamento das respostas do provedor
O cliente HTTP (`RestClient` + `JdkClientHttpRequestFactory`) tem timeouts explícitos: conexão `2s` e leitura `5s`, configuráveis por variável de ambiente. Toda falha é registrada em `failureReason` e a solicitação termina em `FAILED`; o POST continua retornando `201`, pois a solicitação foi criada.

| Resposta do provedor | Status final | `failureReason` | Nova tentativa? |
|---|---|---|---|
| 2xx | `COMPLETED` | — | Não se aplica |
| 400 / outros 4xx | `FAILED` | `Ativacao rejeitada pelo provedor (HTTP 400).` | **Não**: erro de negócio/dados; repetir daria o mesmo resultado |
| 429 | `FAILED` | `Provedor limitou as requisicoes (HTTP 429).` | Sim, em produção, respeitando `Retry-After` |
| 500 / 503 / 5xx | `FAILED` | `Erro interno ou indisponibilidade do provedor (HTTP 5xx).` | Sim, em produção: falha potencialmente temporária |
| Timeout / conexão | `FAILED` | `Tempo limite excedido ou falha de conexao com o provedor.` | Sim, em produção, **sempre com a mesma `Idempotency-Key`**: o provedor pode ter ativado e apenas não respondido |

**Decisão desta versão:** não há retry automático. Como o processamento é síncrono e dentro da requisição do cliente, repetir chamadas aumentaria a latência do POST e manteria a transação aberta por mais tempo. Os erros transitórios (429, 5xx e timeout) ficam identificados pela mensagem para uma evolução com retry assíncrono.

**Estratégia proposta para produção (não implementada):** máximo de 3 tentativas para erros transitórios, com backoff exponencial e jitter (ex.: 1s, 2s, 4s ± aleatório) ou o valor de `Retry-After` quando presente. Ao atingir o limite, marcar `FAILED` e gerar alerta. As novas tentativas seriam feitas por um worker a partir de uma tabela outbox, e não na thread HTTP. Um job de reconciliação reprocessaria solicitações presas em `PROCESSING` por mais tempo que o limite, consultando o provedor pela chave de idempotência.

## Exemplos com curl
Criar:
```bash
curl -i -X POST http://localhost:8080/api/v1/requests -H "Content-Type: application/json" -d '{"externalId":"pedido-1001","customerId":"cliente-42","productCode":"PRODUTO-A"}'
```

Duplicidade (repetir exatamente o POST anterior): espera-se `409 Conflict`.

Falha simulada do provedor:
```bash
curl -i -X POST http://localhost:8080/api/v1/requests -H "Content-Type: application/json" -d '{"externalId":"pedido-1002","customerId":"cliente-42","productCode":"PROD-FAIL"}'
```
A solicitação fica com status `FAILED` e a API retorna `201` com o recurso criado e seu status final; isso diferencia a criação da solicitação do resultado da ativação.

Consulta: `curl http://localhost:8080/api/v1/requests/<UUID>`

## Erros e códigos HTTP
Todas as respostas de erro usam Problem Details (RFC 9457) com `traceId`. Erros inesperados são registrados no log com o mesmo `traceId`, sem stack trace na resposta.
- `400 Bad Request`: campos inválidos (lista em `errors`), JSON malformado ou ausente, UUID inválido no `GET`.
- `404 Not Found`: UUID não encontrado.
- `409 Conflict`: `externalId` já utilizado.
- `405` / `415`: método ou content type não suportados (status preservado do Spring).
- `500 Internal Server Error`: falha inesperada, sem expor stack trace.

### Prevenção de duplicidade
O `externalId` é protegido por uma constraint `UNIQUE` no PostgreSQL (`uk_activation_external_id`, migration V1), além de uma verificação prévia na aplicação. A verificação prévia só evita trabalho no caso comum; a constraint é a garantia final contra concorrência.
- **Mesmo `externalId`, mesmo conteúdo ou conteúdo diferente:** `409 Conflict`. O cliente consulta o recurso existente pelo `GET`. Tratar os dois casos igualmente deixa a regra simples e evita que um reenvio com outro `productCode` seja aceito silenciosamente.
- **Duas requisições idênticas em paralelo:** ambas podem passar pela verificação prévia, mas o banco aceita apenas um `INSERT`. O `saveAndFlush` força o `INSERT` antes da chamada ao provedor. A primeira a gravar recebe `201`; a outra recebe `DataIntegrityViolationException`, convertida em `409`, e não chama o provedor.

## Testes
```bash
mvn test
```

Testes unitários (JUnit 5 + Mockito): sucesso, duplicidade, falha do provedor e consulta inexistente no serviço.

Testes de integração:
- `ActivationApiApplicationTests` (Spring Boot + MockMvc + H2, provedor mockado): criação e consulta, duplicidade (`409`), validação (`400`), JSON malformado, UUID inválido, `404` e solicitação `FAILED` após erro do provedor.
- `HttpActivationProviderTest`: cliente HTTP real contra um servidor HTTP local, cobrindo `204`, `400`, `500`, timeout de leitura e envio da `Idempotency-Key`.

Não implementados nesta versão: teste de concorrência contra PostgreSQL real com Testcontainers (a dependência já está no `pom.xml`) e testes de segurança e de arquitetura.

## Decisões técnicas e limitações conhecidas
1. **Processamento síncrono:** simples de demonstrar em um desafio curto. Em alto volume ou com provedor lento, considerar aceitar a solicitação e retornar `202 Accepted`, processar via fila/outbox e consultar o status posteriormente.
2. **Transação e chamada remota:** nesta versão simplificada a chamada ocorre dentro da transação de serviço. Em produção, evitar manter transação DB aberta durante I/O remoto; usar outbox transacional e worker, com idempotência no provedor.
3. **Timeout/retry:** timeouts de conexão e leitura estão configurados. Retry não está implementado; a estratégia proposta está em "Tratamento das respostas do provedor".
4. **Segurança:** a API do desafio fica sem autenticação para facilitar testes no Swagger. A proteção proposta está descrita na seção "Segurança" e **não está implementada**.
5. **Observabilidade:** Actuator expõe health/metrics; logs não devem conter tokens ou dados sensíveis. Adicionar tracing distribuído e métricas de latência/erros em ambiente real.
6. **CI/CD:** workflow do GitHub Actions executa testes e build Maven. Configurar branch protection e análise estática/scan de dependências em uma evolução.

## Segurança (proposta, não implementada)
Não é necessário criar um servidor de autenticação; o serviço atuaria como **OAuth 2.0 Resource Server** com `spring-boot-starter-oauth2-resource-server`, confiando em um Authorization Server existente (Keycloak, Entra ID, Okta, Cognito).

1. **Fluxo:** **Client Credentials**. Os consumidores são sistemas, não usuários; não há interação humana nem necessidade de refresh token. Cada sistema recebe `client_id`/`client_secret` (ou `private_key_jwt`/mTLS) e obtém um access token de curta duração.
2. **Validação do JWT:**
   - **Assinatura:** RS256/ES256 com chave pública do emissor; HS256 com segredo compartilhado é rejeitado.
   - **Origem das chaves:** endpoint **JWKS** do emissor (`spring.security.oauth2.resourceserver.jwt.issuer-uri` ou `jwk-set-uri`). O `NimbusJwtDecoder` faz cache das chaves e busca a nova chave quando aparece um `kid` desconhecido, o que permite rotação sem deploy.
   - **Emissor:** `iss` deve ser igual ao `issuer-uri` configurado.
   - **Audiência:** `aud` deve conter `activation-api` (`spring.security.oauth2.resourceserver.jwt.audiences`).
   - **Expiração:** `exp` e `nbf` validados com tolerância pequena de relógio (ex.: 60s).
   - Token ausente ou inválido retorna `401`.
3. **Autorização por escopo:**
   ```java
   http.authorizeHttpRequests(auth -> auth
       .requestMatchers(HttpMethod.POST, "/api/v1/requests").hasAuthority("SCOPE_activation.write")
       .requestMatchers(HttpMethod.GET, "/api/v1/requests/**").hasAuthority("SCOPE_activation.read")
       .requestMatchers("/actuator/health/**").permitAll()
       .anyRequest().authenticated())
     .oauth2ResourceServer(o -> o.jwt(Customizer.withDefaults()));
   ```
   Um sistema de vendas teria `activation.write`; um portal de atendimento apenas `activation.read`. Escopo insuficiente retorna `403`. O simulador `/provider/**` não existiria em produção.
4. **Credenciais do provedor externo:** nunca no repositório nem na imagem. Ficam em um gerenciador de segredos (AWS Secrets Manager, Azure Key Vault, HashiCorp Vault) e são injetadas como variável de ambiente ou montadas via Secret do Kubernetes/CSI. A rotação seria automática e periódica, com sobreposição: as credenciais nova e antiga ficam válidas por um período, a aplicação recarrega sem downtime (ex.: Spring Cloud Vault/refresh) e a antiga é revogada depois. Se o provedor aceitar OAuth, o próprio serviço usaria Client Credentials com tokens curtos. Tráfego sempre em TLS, e tokens/segredos nunca são logados.

## Cenário de produção: investigação de lentidão
Dados: API de 300 ms para 2,5 s; algumas queries de 50 ms para 1,2 s; provedor de 400 ms para 3 s; CPU e memória sem alteração relevante informada.

**Leitura inicial:** como o POST chama o provedor de forma síncrona, a latência do provedor entra diretamente no tempo da API. As queries lentas somam-se a isso. CPU e memória estáveis indicam espera em I/O (rede e banco), não falta de processamento, e por isso escalar CPU/memória não seria a primeira ação.

1. **Onde está o tempo:** separar latência por endpoint (`http.server.requests` do Actuator, p50/p95/p99). Se o `GET` continua rápido e só o `POST` piorou, o provedor é o principal suspeito. Se ambos pioraram, o banco também contribui. Com tracing (Micrometer Tracing/OpenTelemetry), o span de cada requisição mostra quanto tempo foi gasto em JDBC, na chamada HTTP e na aplicação.
2. **Banco:** identificar as queries lentas com `pg_stat_statements` e o log de queries lentas (`log_min_duration_statement`), e rodar `EXPLAIN (ANALYZE, BUFFERS)` para procurar seq scans, índices ausentes e estatísticas desatualizadas. Verificar locks (`pg_locks`, `pg_stat_activity`), crescimento da tabela, vacuum e saturação do pool Hikari (`hikaricp.connections.pending`, `acquire`).
3. **Provedor externo:** medir a latência e os códigos de resposta da chamada HTTP (métricas do cliente, como `http.client.requests`). Um provedor em 3 s mantém threads Tomcat e **conexões do pool presas**, porque a chamada ocorre dentro da transação. Isso pode explicar parte da lentidão do banco: requisições esperando conexão livre. Confirmar o status page ou o SLA com o fornecedor.
4. **Correlação:** usar o `traceId` nos logs e nos traces para seguir requisições lentas de ponta a ponta. Comparar a janela da degradação com deploys, migrations, picos de volume e mudanças no provedor.
5. **Impacto:** verificar se o `GET`, o health check e outras operações também degradaram por compartilharem o pool de threads e de conexões. Medir taxa de erro, timeouts e o número de solicitações `FAILED`/`PROCESSING`.
6. **Correção:**
   - Curto prazo: reduzir os timeouts do provedor para um valor coerente com o SLA, criar ou ajustar índices nas queries lentas e dimensionar o pool.
   - Estrutural: tirar a chamada ao provedor da transação e da requisição (`202 Accepted` + outbox + worker) e usar circuit breaker para falhar rápido quando o provedor degradar.
7. **Confirmação:** p95/p99 de volta ao baseline (~300 ms), queries de volta a ~50 ms em `pg_stat_statements`, pool Hikari sem espera e taxa de erro normal, observados após a mudança e com volume equivalente. Criar alertas de latência por dependência para detectar recorrência.

