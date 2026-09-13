## 1. Infraestrutura

- [x] 1.1 ~~Adicionar serviço `zipkin` ao `docker-compose.yml`~~ Substituído: imagem oficial `openzipkin/zipkin` não embarca o módulo `zipkin-collector-otlp` (confirmado via inspeção do classpath do container — sem `zipkin-collector-otlp-*.jar`), receiver OTLP nunca existiu (`404` em `/v1/traces` mesmo com `COLLECTOR_OTLP_HTTP_ENABLED=true`). Aplicado o fallback já previsto no Risco 2 deste documento: `tempo` (Grafana Tempo, OTLP nativo) + `grafana` (única UI de consulta de traces do Tempo) adicionados ao `docker-compose.yml`, com config em `infra/tempo/tempo.yaml` e datasource provisionado em `infra/grafana/provisioning/datasources/tempo.yaml`.
- [x] 1.2 ~~Habilitar/validar o receiver OTLP do Zipkin~~ N/A — ver 1.1. Receiver OTLP validado no Tempo (`distributor.receivers.otlp` em `tempo.yaml`, portas 4317/gRPC e 4318/HTTP).
- [x] 1.3 (novo) Habilitar propagação de trace no Spring AMQP em cada serviço: `spring.rabbitmq.template.observation-enabled=true` e `spring.rabbitmq.listener.simple.observation-enabled=true`. Diferente de HTTP, a instrumentação do `spring-rabbit` **não** é automática só com Micrometer Tracing no classpath — é opt-in por essas duas propriedades (achado ao validar que o log do `estoque-service` não mostrava `traceId`/`spanId` ao consumir mensagem).
- [x] 1.4 (novo) Adicionar `spring-boot-starter-actuator` ao `pom.xml` dos 3 serviços — a autoconfiguração de tracing (`Tracer` bean, correlação de log, exporter OTLP) vive em `spring-boot-actuator-autoconfigure`, só ativa com actuator no classpath. Sem isso, `micrometer-tracing-bridge-otel` fica presente no jar mas sem efeito nenhum.

## 2. produto-service

- [x] 2.1 Adicionar dependências `micrometer-tracing-bridge-otel`, `opentelemetry-exporter-otlp` e `spring-boot-starter-actuator` ao `pom.xml`
- [x] 2.2 Configurar `management.tracing.sampling.probability=1.0`, `management.otlp.tracing.endpoint` (Tempo, `http://localhost:4318/v1/traces`) e `spring.rabbitmq.template/listener.simple.observation-enabled=true` no `application.yml`
- [x] 2.3 Validar que o log console mostra `traceId`/`spanId` ao processar uma requisição

## 3. estoque-service

- [x] 3.1 Adicionar dependências `micrometer-tracing-bridge-otel`, `opentelemetry-exporter-otlp` e `spring-boot-starter-actuator` ao `pom.xml`
- [x] 3.2 Configurar `management.tracing.sampling.probability=1.0`, `management.otlp.tracing.endpoint` (Tempo) e `spring.rabbitmq.template/listener.simple.observation-enabled=true` no `application.yml`
- [x] 3.3 Validar que o log console mostra `traceId`/`spanId` ao consumir uma mensagem

## 4. pedido-service

- [x] 4.1 Adicionar dependências `micrometer-tracing-bridge-otel`, `opentelemetry-exporter-otlp` e `spring-boot-starter-actuator` ao `pom.xml`
- [x] 4.2 Configurar `management.tracing.sampling.probability=1.0`, `management.otlp.tracing.endpoint` (Tempo) e `spring.rabbitmq.template/listener.simple.observation-enabled=true` no `application.yml`
- [x] 4.3 Validar que o log console mostra `traceId`/`spanId` ao processar uma requisição

## 5. Validação end-to-end

- [x] 5.1 Subir os 3 serviços + infraestrutura (incluindo `tempo` e `grafana`) via docker-compose
- [x] 5.2 Criar um produto, abrir Grafana (`http://localhost:3000`, login anônimo habilitado) → Explore → datasource Tempo, e confirmar trace atravessando produto-service → estoque-service e produto-service → pedido-service (fanout de `product-changed`) — validado via API do Tempo (`/api/traces/{traceId}`), spans dos 3 serviços presentes no mesmo trace
- [x] 5.3 Criar um pedido, confirmar trace único atravessando pedido-service → estoque-service → pedido-service (saga de reserva via RabbitMQ) — validado via API do Tempo: 5 spans (`http post /pedidos` → send `reservation-requested` → estoque-service receive → send `reservation-processed` → pedido-service receive), todos no mesmo `traceId`
- [x] 5.4 Pegar um `traceId` do Tempo/Grafana e confirmar que aparece nos logs de todos os serviços envolvidos naquele trace
