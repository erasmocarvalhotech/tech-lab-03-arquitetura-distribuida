# tech-lab-03-arquitetura-distribuida

Laboratório técnico de arquitetura distribuída — projeto de microsserviços para cadastro de produtos, pedidos e controle de estoque.

## Objetivo

Explorar padrões de arquitetura distribuída (comunicação assíncrona entre serviços, persistência isolada por domínio, saga coreografada, resiliência de mensageria) através de três microsserviços:

- **produto-service**: cadastro e consulta de produtos (catálogo — nome, descrição, SKU, preço). Nunca exclui um produto fisicamente, só inativa.
- **estoque-service**: controle de saldo e reserva de estoque por produto. Único dono da informação de quantidade.
- **pedido-service**: criação e consulta de pedidos. Mantém uma projeção local (Redis) dos dados de produto, alimentada por eventos, sem nenhuma chamada síncrona a `produto-service`.

## Arquitetura

- Java 21 / Spring Boot, camadas Controller/Service/Repository/Entity em cada serviço.
- Um banco PostgreSQL por serviço (database-per-service): `db_produto`, `db_estoque`, `db_pedido`.
- Comunicação 100% assíncrona entre os três serviços via RabbitMQ — sem REST síncrono serviço-a-serviço.
- Reserva de estoque via saga coreografada (`reservation-requested` / `reservation-processed`) com lock otimista (`@Version`), reaproveitando o fluxo de retry `-delayed`/`-failed` das filas.
- Cache Redis no `pedido-service` (não no `produto-service`) — projeção local alimentada pelo stream `product-changed`.

Detalhes completos: decisões e trade-offs originais em [openspec/changes/archive/2026-09-13-arquitetura-microservicos/](openspec/changes/archive/2026-09-13-arquitetura-microservicos/) (`design.md`), specs canônicas em [openspec/specs/](openspec/specs/) (`cadastro-produto`, `controle-estoque`, `cadastro-pedido`); diagrama C4 de containers em [docs/arquitetura-c4-container.drawio](docs/arquitetura-c4-container.drawio); convenção de nomenclatura de filas/exchanges em [docs/taxonomia-filas-rabbitmq.md](docs/taxonomia-filas-rabbitmq.md).

Observabilidade — tracing distribuído (traceId/spanId correlacionados nos 3 serviços) e logs centralizados, com trace-to-logs no Grafana: [openspec/specs/observabilidade/](openspec/specs/observabilidade/) (spec canônica) e guia de uso em [docs/guia-grafana.md](docs/guia-grafana.md).

## Status

Os 3 microsserviços implementados, testados e integrados via RabbitMQ (fanout de `product-changed`, saga de reserva). Observabilidade completa: tracing distribuído (Tempo) e logs centralizados (Loki), correlacionados por `traceId`/`spanId` com trace-to-logs no Grafana.
