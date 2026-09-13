## 1. Infraestrutura

- [ ] 1.1 Adicionar serviço `zipkin` ao `docker-compose.yml` (imagem `openzipkin/zipkin`, porta 9411, storage in-memory)
- [ ] 1.2 Habilitar/validar o receiver OTLP do Zipkin (variável de ambiente do container — confirmar na documentação da imagem `openzipkin/zipkin` a flag correta, ex.: `COLLECTOR_OTLP_HTTP_ENABLED`); se não for viável, avaliar troca do backend por Grafana Tempo (Risco documentado no design.md)

## 2. produto-service

- [ ] 2.1 Adicionar dependências `micrometer-tracing-bridge-otel` e `opentelemetry-exporter-otlp` ao `pom.xml`
- [ ] 2.2 Configurar `management.tracing.sampling.probability=1.0` e `management.otlp.tracing.endpoint` (endpoint OTLP do Zipkin/coletor) no `application.yml`
- [ ] 2.3 Validar que o log console mostra `traceId`/`spanId` ao processar uma requisição

## 3. estoque-service

- [ ] 3.1 Adicionar dependências `micrometer-tracing-bridge-otel` e `opentelemetry-exporter-otlp` ao `pom.xml`
- [ ] 3.2 Configurar `management.tracing.sampling.probability=1.0` e `management.otlp.tracing.endpoint` no `application.yml`
- [ ] 3.3 Validar que o log console mostra `traceId`/`spanId` ao consumir uma mensagem

## 4. pedido-service

- [ ] 4.1 Adicionar dependências `micrometer-tracing-bridge-otel` e `opentelemetry-exporter-otlp` ao `pom.xml`
- [ ] 4.2 Configurar `management.tracing.sampling.probability=1.0` e `management.otlp.tracing.endpoint` no `application.yml`
- [ ] 4.3 Validar que o log console mostra `traceId`/`spanId` ao processar uma requisição

## 5. Validação end-to-end

- [ ] 5.1 Subir os 3 serviços + infraestrutura (incluindo `zipkin`) via docker-compose
- [ ] 5.2 Criar um produto, abrir Zipkin UI (`http://localhost:9411`) e confirmar trace atravessando produto-service → estoque-service e produto-service → pedido-service (fanout de `product-changed`)
- [ ] 5.3 Criar um pedido, confirmar trace único atravessando pedido-service → estoque-service → pedido-service (saga de reserva via RabbitMQ)
- [ ] 5.4 Pegar um `traceId` do Zipkin e confirmar que aparece nos logs de todos os serviços envolvidos naquele trace
