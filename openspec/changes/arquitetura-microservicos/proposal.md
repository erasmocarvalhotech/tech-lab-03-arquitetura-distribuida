## Why

O laboratório precisa de uma base arquitetural de microsserviços distribuídos antes de qualquer implementação: hoje o repositório só tem o setup do projeto (OpenSpec, CodeGraph), sem nenhum serviço definido. Sem formalizar os limites de domínio, os contratos de mensageria e as decisões de consistência agora, cada serviço seria implementado de forma ad-hoc e inconsistente entre si.

## What Changes

- Define três microsserviços Java 21/Spring Boot: `produto-service`, `pedido-service`, `estoque-service`, cada um com banco PostgreSQL próprio (database-per-service) e camadas Controller/Service/Repository/Entity.
- Define comunicação 100% assíncrona entre os três serviços via RabbitMQ — nenhuma chamada REST síncrona serviço-a-serviço.
- Define o stream de eventos `product-changed` (exchange `mb-techlab-product-exchange-topic-product-changed`, tipo de ação via header `event-type`: created/updated/deactivated) com fanout para duas filas independentes: uma consumida por `estoque-service` (sincroniza saldo) e outra por `pedido-service` (atualiza cache local de produto). Nomenclatura completa em `docs/taxonomia-filas-rabbitmq.md`.
- Define que `produto-service` nunca exclui fisicamente um produto — "remover" é sempre inativação (`ativo=false`), preservando o registro para não gerar referências órfãs em `estoque-service`/`pedido-service`.
- Define a saga coreografada de reserva de estoque entre `pedido-service` e `estoque-service` via os streams `reservation-requested`/`reservation-processed`, com reserva por lock otimista (`@Version`) — conflito de concorrência reaproveita o fluxo `-delayed` como retry.
- Define fronteiras de domínio: `produto-service` é dono de nome/preço/SKU; `estoque-service` é o único dono de quantidade (produto nasce com saldo zero; carga inicial de estoque é ação separada, não embutida na criação do produto).
- Define cache Redis local no `pedido-service` (projeção de nome/preço/ativo mantida via consumo do stream `product-changed`), usado para snapshot de preço no item do pedido sem chamada síncrona a Produto.
- Define resiliência de mensageria em 3 camadas por fila principal (`-delayed` com retry via TTL+DLX, `-failed` como DLQ definitiva).

## Capabilities

### New Capabilities
- `cadastro-produto`: CRUD de produtos (nome, descrição, SKU, preço) sem exclusão física, publicação do stream `product-changed` (headers created/updated/deactivated).
- `controle-estoque`: saldo de estoque por produto, consumo de `product-changed` para inicializar saldo, reserva de estoque via `reservation-requested` com resposta em `reservation-processed`.
- `cadastro-pedido`: criação e consulta de pedidos, cache local de dados de produto (Redis) alimentado por `product-changed`, consumo de `reservation-processed` para confirmar ou rejeitar o pedido.

### Modified Capabilities
(nenhuma — projeto greenfield, sem specs existentes)

## Impact

- Novo código: três módulos/serviços Spring Boot independentes (sem código compartilhado além de contratos de evento documentados).
- Infraestrutura: 3 instâncias PostgreSQL (uma por serviço), 1 instância RabbitMQ, 1 instância Redis.
- Sem impacto em código existente — repositório hoje só tem setup (`.claude/`, `openspec/`, `docs/`).
- Diagrama de referência já versionado em `docs/arquitetura-c4-container.drawio`.
