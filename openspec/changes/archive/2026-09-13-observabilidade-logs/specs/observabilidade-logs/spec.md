## ADDED Requirements

### Requirement: Log persistido em arquivo por serviço
Cada um dos três serviços (`produto-service`, `estoque-service`, `pedido-service`) SHALL escrever seu log em arquivo local, além do console, sem alterar o padrão de formatação já usado no console (incluindo a correlação `traceId`/`spanId`).

#### Scenario: Serviço processa uma requisição
- **WHEN** o serviço loga uma linha no console (ex.: `INFO ... Produto criado: id=24`)
- **THEN** a mesma linha SHALL aparecer também no arquivo de log do serviço em `./logs/<service>/app.log`

### Requirement: Log centralizado e consultável no Loki
O log dos três serviços SHALL ser coletado e armazenado no Loki, rotulado por nome do serviço, sem exigir que os serviços rodem em container Docker.

#### Scenario: Log de um serviço aparece no Loki
- **WHEN** o serviço grava uma linha de log em `./logs/<service>/app.log`
- **THEN** essa linha SHALL estar disponível pra consulta no Loki em até alguns segundos, com o label `service_name` correspondente

#### Scenario: Filtrar log por serviço no Grafana
- **WHEN** um usuário abre o Grafana Explore, seleciona o datasource Loki e filtra por `service_name="pedido-service"`
- **THEN** o resultado SHALL conter só as linhas de log do `pedido-service`, sem log dos outros dois serviços

### Requirement: Correlação trace-to-logs no Grafana
A partir de um span de um trace no datasource Tempo, o Grafana SHALL permitir pular direto pros logs daquele mesmo `traceId` no Loki, sem o usuário precisar copiar/colar a string manualmente.

#### Scenario: Usuário navega de um trace pro log correspondente
- **WHEN** um usuário abre um trace no Tempo (Grafana Explore) e clica na ação de ver logs de um span
- **THEN** o Grafana SHALL abrir uma consulta no Loki já filtrada pelo `traceId` daquele trace, retornando as linhas de log de todos os serviços que participaram daquele trace
