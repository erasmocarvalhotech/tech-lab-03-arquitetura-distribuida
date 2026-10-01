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

## Como subir o projeto

Pré-requisitos: Java 21, Maven 3.9+, Docker + Docker Compose.

1. Suba toda a infraestrutura (3 Postgres, RabbitMQ, Redis, Tempo, Loki, Alloy, Grafana) a partir da raiz do repositório:

   ```bash
   docker compose up -d
   ```

   Aguarde ficarem `healthy`:

   ```bash
   docker compose ps
   ```

2. Suba os três serviços, cada um em um terminal, a partir da sua própria pasta:

   ```bash
   cd produto-service && mvn spring-boot:run
   cd estoque-service && mvn spring-boot:run
   cd pedido-service  && mvn spring-boot:run
   ```

   Cada serviço roda suas próprias migrations Flyway automaticamente na inicialização. Detalhes, variáveis de ambiente e exemplos de requisição no README de cada um: [produto-service](produto-service/README.md), [estoque-service](estoque-service/README.md), [pedido-service](pedido-service/README.md).

### URLs e credenciais

| Serviço | URL | Usuário / senha |
|---|---|---|
| produto-service (REST) | `http://localhost:8081` | — |
| estoque-service (REST) | `http://localhost:8082` | — |
| pedido-service (REST) | `http://localhost:8083` | — |
| RabbitMQ (painel de management) | `http://localhost:15672` | `techlab` / `techlab` |
| RabbitMQ (AMQP) | `localhost:5672` | `techlab` / `techlab` |
| Grafana (tracing + logs) | `http://localhost:3000` | login anônimo (Admin), sem senha |
| Postgres `db_produto` | `localhost:5432` | `techlab` / `techlab` |
| Postgres `db_estoque` | `localhost:5436` | `techlab` / `techlab` |
| Postgres `db_pedido` | `localhost:5434` | `techlab` / `techlab` |
| Redis (cache do pedido-service) | `localhost:6379` | sem senha |

Guia de uso do Grafana (busca de traces, correlação trace-to-logs): [docs/guia-grafana.md](docs/guia-grafana.md).

## Status

Os 3 microsserviços implementados, testados e integrados via RabbitMQ (fanout de `product-changed`, saga de reserva). Observabilidade completa: tracing distribuído (Tempo) e logs centralizados (Loki), correlacionados por `traceId`/`spanId` com trace-to-logs no Grafana.
