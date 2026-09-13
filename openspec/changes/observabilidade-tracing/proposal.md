## Why

Hoje não há como acompanhar uma única transação de negócio (ex.: um pedido) através dos três serviços. Cada serviço loga de forma isolada, sem nenhum identificador compartilhado entre eles — quando algo falha em produção (ex.: um pedido ficou preso em `PENDENTE`), não existe forma de correlacionar os logs de `produto-service`, `estoque-service` e `pedido-service` referentes àquela mesma transação, mesmo sabendo o `pedidoId`. Isso já estava registrado como risco aceito na spec anterior (`arquitetura-microservicos`, arquivada), a ser tratado como mudança separada.

## What Changes

- Adiciona tracing distribuído aos três serviços via Micrometer Tracing + bridge OpenTelemetry, exportando spans via OTLP para um **Grafana Tempo** local (troca em relação à decisão original — ver nota no design.md: a imagem oficial do Zipkin não embarca o receiver OTLP).
- Adiciona serviços `tempo` e `grafana` ao `docker-compose.yml` (Grafana como única UI de consulta de traces do Tempo — não inclui dashboards de métricas de negócio, o Non-Goal original permanece válido).
- Propagação de contexto de trace é automática — Spring Boot instrumenta tanto as chamadas HTTP quanto o `spring-rabbit`, então o trace atravessa a fila RabbitMQ sem código manual de propagação.
- Logs de cada serviço passam a incluir `traceId`/`spanId` (padrão default do Spring Boot já ativa isso quando Micrometer Tracing está no classpath).

## Capabilities

### New Capabilities
- `observabilidade`: tracing distribuído entre os três serviços (HTTP + RabbitMQ) e logs correlacionados por `traceId`, com Grafana Tempo como backend de visualização.

### Modified Capabilities
(nenhuma — as capacidades `cadastro-produto`, `controle-estoque` e `cadastro-pedido` não mudam de comportamento de negócio; ganham apenas instrumentação transversal)

## Impact

- Novos containers `tempo` e `grafana` na infraestrutura local (`docker-compose.yml`), configs em `infra/tempo/tempo.yaml` e `infra/grafana/provisioning/`.
- Cada serviço ganha 3 dependências novas no `pom.xml` (`micrometer-tracing-bridge-otel`, `opentelemetry-exporter-otlp`, `spring-boot-starter-actuator` — necessária pra autoconfiguração de tracing funcionar) e configuração de amostragem/endpoint OTLP + propagação AMQP (`spring.rabbitmq.template/listener.simple.observation-enabled=true`) no `application.yml`.
- Sem mudança de contrato de API ou de payload de evento — é instrumentação, não lógica de negócio.
