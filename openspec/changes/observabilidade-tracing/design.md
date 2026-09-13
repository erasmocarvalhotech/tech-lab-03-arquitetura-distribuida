## Context

Os três microsserviços (`produto-service`, `estoque-service`, `pedido-service`) estão implementados, testados e integrados (spec `arquitetura-microservicos`, arquivada). A comunicação entre eles é 100% assíncrona via RabbitMQ. Não existe hoje nenhum identificador compartilhado entre os logs dos três serviços — rastrear uma transação de ponta a ponta depende de inspecionar cada log manualmente e cruzar por `pedidoId`/`produtoId`, quando esses IDs aparecem no log.

## Goals / Non-Goals

**Goals:**
- Permitir seguir uma transação (ex.: criação de um pedido) através dos três serviços por um único identificador.
- Propagação automática do contexto de trace por HTTP e por RabbitMQ, sem código manual em cada publisher/listener.
- Logs de cada serviço correlacionados ao mesmo identificador de trace.
- Visualização do trace completo (waterfall) numa UI local.

**Non-Goals:**
- Não é objetivo configurar amostragem realista de produção (usa-se 100% de amostragem no lab — em produção seria uma fração).
- Não é objetivo adicionar métricas de negócio ou dashboards (Grafana/Prometheus) nesta mudança — só tracing.
- Não é objetivo mudar qualquer contrato de API ou payload de evento existente.

## Decisions

### 1. Micrometer Tracing + bridge OpenTelemetry + Zipkin (via OTLP), em vez de `correlation-id` manual

**Por quê**: a autoinstrumentação do Spring Boot já cobre HTTP (controllers) e `spring-rabbit` (publishers e listeners) — o contexto de trace viaja nos **headers da própria mensagem RabbitMQ** automaticamente. Implementar isso manualmente exigiria gerar um id na entrada, propagá-lo em cada publisher (adicionar header), extraí-lo em cada listener (ler header, colocar no MDC) e repetir isso nos três serviços — mais código, mais chance de divergência entre eles, e sem UI de visualização ao final.

**Bridge OTel em vez de Brave**: o Micrometer Tracing suporta duas pontes — `micrometer-tracing-bridge-brave` (motor original do Zipkin) e `micrometer-tracing-bridge-otel` (OpenTelemetry). Optamos pela ponte **OTel**, exportando via **OTLP** (`OpenTelemetry Protocol`), por ser o padrão de fato da indústria hoje — inclusive dentro do próprio Spring (o Spring Cloud Sleuth, historicamente ligado a Brave/Zipkin, foi descontinuado e absorvido pelo Micrometer Tracing; a documentação e o ecossistema atual do Spring Boot 3 apontam pro bridge OTel como a opção mais alinhada). OTLP é aceito nativamente por praticamente todo backend relevante — Datadog, Grafana Tempo/Cloud, Honeycomb, New Relic, e o próprio Zipkin (via receiver OTLP) — o que torna esse setup mais transferível pra um ambiente real (ex.: trocar o exporter Zipkin por um Datadog Agent local, sem reinstrumentar nada no código).

**Alternativa considerada (bridge)**: `micrometer-tracing-bridge-brave`, o caminho historicamente mais simples de conectar com Zipkin (integração nativa, sem precisar habilitar receiver OTLP no Zipkin). Rejeitada por representar um padrão de protocolo cada vez mais tratado como legado — o objetivo do lab é também ensinar o padrão que se encontra na indústria hoje, não só "o que funciona mais fácil".

**Alternativa considerada (correlation-id manual)**: `correlation-id` próprio via MDC + header de mensagem manual (`correlation-id`, ao lado do `event-type` que já existe nos streams). Mais simples de entender o mecanismo por dentro (útil como material didático complementar), mas não foi adotada como implementação principal — ficaria pra uma discussão à parte se o grupo quiser entender "o que a instrumentação automática faz por baixo dos panos".

**Zipkin como backend**: mantido como backend/UI de visualização mesmo com a troca pro bridge OTel — o Zipkin moderno aceita ingestão via OTLP (receiver a habilitar/validar na Etapa de implementação), continuando como um único container simples, sem storage externo, adequado ao escopo de um lab. Se o receiver OTLP do Zipkin se mostrar limitado na prática, Grafana Tempo (nativamente OTLP-first) é a alternativa mais próxima a considerar.

### 2. Amostragem 100% (`management.tracing.sampling.probability=1.0`)

**Por quê**: em produção, amostrar 100% dos traces tem custo de performance e volume de dados; num lab, queremos ver todo trace gerado, sem surpresa de "cadê o trace que eu esperava". Valor documentado explicitamente como decisão de ambiente de lab, não como recomendação de produção.

### 3. Logs não mudam de formato manualmente

**Por quê**: o padrão de log console do Spring Boot já inclui `[%X{traceId:-},%X{spanId:-}]` automaticamente assim que detecta Micrometer Tracing no classpath — não há necessidade de reescrever o `logging.pattern.console` à mão. A tarefa de implementação é validar que isso aparece, não configurar do zero.

## Risks / Trade-offs

| Risco | Mitigação / decisão |
|---|---|
| Zipkin com storage in-memory perde todos os traces ao reiniciar o container | Aceitável para o lab — não é objetivo persistir histórico de traces além da sessão de teste atual. |
| Receiver OTLP do Zipkin pode exigir configuração/flag específica (a confirmar na implementação) | Validar na Etapa 1 (infraestrutura) antes de seguir pros serviços; se o receiver se mostrar problemático, trocar o backend por Grafana Tempo (OTLP nativo) sem impacto na instrumentação dos serviços (a troca fica isolada na configuração do exporter). |
| Amostragem 100% em um cenário de carga real geraria volume alto de spans | Fora do escopo deste lab; documentado como decisão específica de ambiente local, não como recomendação a replicar em produção. |
| Dependência nova em 3 `pom.xml` pode conflitar com versões gerenciadas pelo Spring Boot BOM | Usar as dependências sem versão explícita (herdada do `spring-boot-starter-parent`), consistente com o padrão já usado nos três serviços. |

## Migration Plan

Não aplicável — mudança aditiva (nova infraestrutura + novas dependências), sem alteração de schema de banco, contrato de API ou payload de evento. Pode ser aplicada e testada sem impacto no que já está em produção nesta base.
