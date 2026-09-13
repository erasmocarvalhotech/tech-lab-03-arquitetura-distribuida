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

Detalhes completos: proposta, decisões e trade-offs em [openspec/changes/arquitetura-microservicos/](openspec/changes/arquitetura-microservicos/) (`proposal.md`, `design.md`, `specs/`, `tasks.md`); diagrama C4 de containers em [docs/arquitetura-c4-container.drawio](docs/arquitetura-c4-container.drawio); convenção de nomenclatura de filas/exchanges em [docs/taxonomia-filas-rabbitmq.md](docs/taxonomia-filas-rabbitmq.md).

Tracing distribuído (traceId/spanId correlacionados nos 3 serviços, visualização em Grafana + Tempo): [openspec/changes/observabilidade-tracing/](openspec/changes/observabilidade-tracing/) e guia de uso em [docs/guia-grafana-tempo.md](docs/guia-grafana-tempo.md).

## Status

Arquitetura definida (proposta OpenSpec completa). Implementação dos serviços ainda não iniciada.
