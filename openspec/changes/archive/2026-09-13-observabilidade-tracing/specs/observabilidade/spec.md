## ADDED Requirements

### Requirement: Tracing distribuído entre os três serviços
Os três serviços (`produto-service`, `estoque-service`, `pedido-service`) SHALL propagar um contexto de trace único através de chamadas HTTP e mensagens RabbitMQ, exportando os spans gerados via OTLP para um backend Grafana Tempo, sem exigir código manual de propagação em publishers ou listeners.

#### Scenario: Trace atravessa a saga de reserva de estoque
- **WHEN** um pedido é criado via `POST /pedidos`, disparando a saga (`reservation-requested` → `reservation-processed`)
- **THEN** um único `traceId` aparece nos spans gerados por `pedido-service` (criação do pedido e publicação do evento) e por `estoque-service` (consumo do evento e publicação do resultado), visível como um trace único no Tempo/Grafana

#### Scenario: Trace atravessa o fanout de produto
- **WHEN** um produto é criado via `POST /produtos`, disparando o fanout do stream `product-changed` para `estoque-service` e `pedido-service`
- **THEN** o `traceId` gerado na requisição original aparece nos spans de consumo de ambos os serviços consumidores

#### Scenario: Trace preservado em retentativas de mensagem
- **WHEN** uma mensagem falha ao ser processada e é reciclada via fila `-delayed` (política de retry)
- **THEN** o mesmo `traceId` original é preservado em cada nova tentativa de entrega, aparecendo como spans distintos dentro do mesmo trace

### Requirement: Logs correlacionados por trace
Cada serviço SHALL incluir `traceId` e `spanId` em toda linha de log emitida durante o processamento de uma requisição ou mensagem, sem necessidade de configuração manual de padrão de log.

#### Scenario: Busca por traceId nos logs
- **WHEN** um `traceId` é obtido a partir de uma requisição processada (via log ou via Tempo/Grafana)
- **THEN** o mesmo `traceId` pode ser encontrado nas linhas de log correspondentes de qualquer serviço que tenha participado daquela transação
