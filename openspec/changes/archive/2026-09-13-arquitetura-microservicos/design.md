## Context

Projeto greenfield (laboratório de arquitetura distribuída). Não existe código de serviço ainda — só o setup do repositório. A decisão arquitetural precisa ser tomada antes de qualquer implementação, para que os três serviços nasçam consistentes entre si (mesmo padrão de camadas, mesmo contrato de mensageria, mesma postura sobre consistência).

Diagrama de referência (C4 Container) já versionado em `docs/arquitetura-c4-container.drawio`.

## Goals / Non-Goals

**Goals:**
- Três serviços com fronteiras de domínio claras e banco próprio (database-per-service).
- Zero acoplamento síncrono entre os três serviços — toda comunicação inter-serviço via RabbitMQ.
- Consistência eventual sustentada por eventos de domínio, com reserva de estoque via saga coreografada.
- Simplicidade deliberada: evitar padrões avançados (scaling multi-instância) que não seriam justificados pelo escopo de um laboratório, reaproveitando a infraestrutura de mensageria já existente (filas `-delayed`) em vez de construir mecanismos de retry paralelos.

**Non-Goals:**
- Não é objetivo suportar múltiplas instâncias por serviço nesta fase — decisão explícita de manter simples.
- Não é objetivo ter um serviço dedicado de cache/read-model compartilhado entre múltiplos consumidores (só o `pedido-service` consome dados de produto hoje).
- Não é objetivo garantir *exactly-once* na entrega de eventos — o design aceita *at-least-once* com consumidores idempotentes onde necessário.

## Decisions

### 1. Comunicação 100% assíncrona entre serviços (RabbitMQ), nunca REST síncrono
**Por quê**: qualquer chamada síncrona entre Produto/Pedido/Estoque criaria acoplamento temporal — indisponibilidade de um serviço derrubaria os outros em cascata. Assíncrono puro é também o que dá valor didático ao laboratório (é o que ensina os problemas reais de sistemas distribuídos).
**Alternativa considerada**: REST síncrono para consultas (ex.: Pedido chamando Produto para obter preço). Rejeitada por reintroduzir acoplamento temporal e contrariar o objetivo do laboratório.

### 2. Saga coreografada (sem orquestrador central) para reserva de estoque
Pedido publica `pedido.reserva.solicitada` → Estoque tenta reservar e responde com `estoque.reservado` ou `estoque.indisponivel` → Pedido consome e atualiza seu próprio status.
**Por quê**: com apenas 2 serviços trocando eventos nesse fluxo, uma máquina de estado orquestrada seria complexidade desproporcional ao problema.
**Alternativa considerada**: saga orquestrada com estado central. Rejeitada por agora — reavaliar se o fluxo crescer além de 2 participantes ou precisar de compensações complexas.

### 3. Reserva de estoque via lock otimista (`@Version`), retry via fila `-delayed`
`SaldoEstoque` ganha um campo `@Version`. Ao consumir `reservation-requested`, o `estoque-service` lê o saldo, valida `quantidadeDisponivel >= quantidade` em memória e salva via JPA. Dois desfechos são tratados de forma distinta:

- **Conflito de concorrência** (`OptimisticLockException` — outra transação alterou a linha entre a leitura e o commit): **não** é tratado como resposta de negócio nem retry em memória. A exceção propaga, a mensagem não é confirmada (ack) ao broker, e ela recircula pela fila `-delayed` já existente (TTL + DLX de volta à fila principal) até ser reprocessada com sucesso ou esgotar o limite e cair em `-failed`.
- **Estoque insuficiente** (regra de negócio — quantidade solicitada maior que a disponível, sem conflito de escrita): não é erro técnico, não aciona retry. O `estoque-service` publica `reservation-processed` com resultado `rejected` imediatamente.

**Por quê**: reaproveita o `-delayed`/`-failed` que já existe como retry, sem precisar de mecanismo próprio em código. `@Version` é o padrão idiomático do Spring Data JPA — mais simples que `UPDATE` condicional manual.
**Alternativa considerada**: `UPDATE` condicional via SQL manual. Descartada — não diferencia conflito de concorrência de estoque insuficiente, e não aproveita o `-delayed` já pronto.

### 4. Fronteiras de domínio: Produto não conhece quantidade; Estoque não conhece preço
- `produto-service` é dono de nome/descrição/SKU/preço (dado de catálogo).
- `estoque-service` é o único dono de quantidade disponível/reservada.
- Produto nasce com saldo zero no Estoque; carga inicial de estoque é uma ação própria do Estoque (não embutida no evento `produto.criado`).
**Por quê**: mistura de responsabilidade (ex.: Produto decidindo quantidade inicial) quebraria a fronteira de domínio e criaria acoplamento implícito entre os dois modelos.

**Produto nunca é excluído fisicamente** — "remover" um produto é sempre inativação (`ativo=false`) em `db_produto`, nunca `DELETE`. O evento correspondente se chama `product-deactivated` (não `product-removed`), justamente pra não sugerir exclusão. A remoção da entrada de cache no `pedido-service` ao consumir esse evento é segura porque cache é dado derivado e descartável, sem risco de referência órfã — diferente de excluir a linha física do produto, que deixaria `SaldoEstoque` (Estoque) e `ItemPedido` histórico (Pedido) apontando para um produtoId inexistente.

### 5. Cache Redis do catálogo vive no `pedido-service`, não no `produto-service`
É `pedido-service` quem tem a maior carga de consulta a dados de produto (precisa do preço/nome pra montar cada item de pedido). Em vez de cache-aside com chamada síncrona a Produto (o que violaria a Decisão 1), o cache é uma **projeção local** alimentada pelo próprio `pedido-service` consumindo o stream `product-changed` (eventos `product-created`/`product-updated`/`product-deactivated`, discriminados por header).
**Por quê**: mantém zero acoplamento síncrono e coloca o cache onde o padrão de acesso realmente pede.
**Alternativa considerada**: cache-aside tradicional no `produto-service` sendo chamado por Pedido via REST. Rejeitada por reintroduzir chamada síncrona entre serviços. Também considerado (e rejeitado) um 4º microsserviço dedicado a manter esse cache — criaria banco/cache compartilhado implícito entre serviços (se o novo serviço escreve e o Pedido lê o mesmo Redis) ou reintroduziria a chamada síncrona (se o Pedido chamasse a API desse serviço) — nenhum dos dois resolve o problema, só desloca.

### 6. Nomenclatura de filas/exchanges RabbitMQ e discriminação de evento por header
Recursos seguem a convenção documentada em `docs/taxonomia-filas-rabbitmq.md`: prefixo `mb-`, kebab-case, inglês, padrão `mb-{domain}-exchange-{tipo}-{stream}` / `mb-{domain}-queue-{stream}`. Um único exchange topic por *stream* de evento (não um exchange por ação individual) — o tipo específico da ação (criado/atualizado/inativado, confirmado/rejeitado) viaja no **header** da mensagem (`event-type`), não em routing keys ou exchanges separadas.

Streams definidos: `product-changed` (Produto → Estoque e Pedido, fanout em duas filas independentes), `reservation-requested` (Pedido → Estoque) e `reservation-processed` (Estoque → Pedido). Cada fila principal tem uma fila `-delayed` (retry com backoff via TTL de **1 min** + DLX apontando de volta pra fila principal) e uma fila `-failed` (DLQ definitiva, sem TTL) — três camadas de resiliência por fila, sem vhost dedicado neste lab (ambiente único).
**Por quê**: um exchange por stream (em vez de por ação) evita explosão de recursos (3 exchanges + 6 filas só para eventos de produto, por exemplo) mantendo o discriminador de ação como dado de mensagem, não de topologia. Resiliência em 3 camadas foi adotada integralmente (não só uma DLQ simples) por decisão explícita, mesmo sendo lab.
**Alternativa considerada**: um exchange por ação-realizada (ex.: `product-created`, `product-updated`, `product-deactivated` cada um com seu próprio exchange/routing key), replicando à risca o padrão corporativo de origem. Rejeitada para este lab por gerar volume de recursos desproporcional ao número de consumidores (2) e ao objetivo didático atual.

### 7. Limite de retentativas: 1 tentativa inicial + 2 retries via `-delayed` (3 operações no total), depois `-failed`

A topologia declarativa (exchange/fila/DLX) por si só não conta tentativas — RabbitMQ cicla mensagem entre fila principal e `-delayed` indefinidamente se o consumidor sempre rejeitar. Cabe ao **consumidor** (todo listener que consome fila principal com par `-delayed`/`-failed`: `estoque-service` em `product-changed` e `reservation-requested`; `pedido-service` em `product-changed` e `reservation-processed`) inspecionar o header `x-death` (array que o próprio RabbitMQ preenche a cada dead-letter) e decidir entre rejeitar de novo (mais um ciclo `-delayed`) ou publicar direto na `-failed`.

Regra: procurar no `x-death` a entrada com `queue = <nome-da-fila>-delayed` e `reason = expired` (retorno por TTL). Seu campo `count` indica quantos ciclos de retry já foram concluídos:

- `count` ausente ou `0` → primeira tentativa (original). Falhou → rejeita (nack sem requeue) → cai na `-delayed` (1º retry agendado).
- `count = 1` → esta é a 2ª tentativa (1º retry). Falhou de novo → rejeita → cai na `-delayed` outra vez (2º retry agendado).
- `count = 2` → esta é a 3ª tentativa (2º retry, o último permitido). Falhou de novo → **não rejeita** — publica a mensagem manualmente na fila `-failed` correspondente e confirma (ack) a mensagem original.

Total: 1 tentativa inicial + 2 retries = 3 operações de processamento antes de cair definitivamente em `-failed`.

**Por quê**: `x-death` já vem de graça no protocolo AMQP/RabbitMQ quando dead-lettering é usado — não precisa de tabela de controle nem contador externo. 3 tentativas (1 + 2) é o valor combinado para este lab; ajustável só mudando o limiar de `count` checado no código.

## Modelo de Dados

### db_produto

```sql
CREATE TABLE tb_produto (
    id             BIGSERIAL PRIMARY KEY,
    nome           VARCHAR(200) NOT NULL,
    descricao      VARCHAR(1000),
    sku            VARCHAR(50) NOT NULL UNIQUE,
    preco          NUMERIC(12,2) NOT NULL,
    ativo          BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em      TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em  TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

### db_estoque

```sql
CREATE TABLE tb_saldo_estoque (
    id                     BIGSERIAL PRIMARY KEY,
    produto_id             BIGINT NOT NULL UNIQUE, -- referencia logica a tb_produto.id; sem FK fisica (banco de outro servico)
    quantidade_disponivel  INTEGER NOT NULL DEFAULT 0,
    quantidade_reservada   INTEGER NOT NULL DEFAULT 0,
    version                BIGINT NOT NULL DEFAULT 0, -- @Version (lock otimista, Decisao 3)
    atualizado_em          TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

### db_pedido

```sql
CREATE TABLE tb_pedido (
    id             BIGSERIAL PRIMARY KEY,
    status         VARCHAR(30) NOT NULL, -- PENDENTE | CONFIRMADO | REJEITADO_SEM_ESTOQUE
    criado_em      TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE tb_item_pedido (
    id              BIGSERIAL PRIMARY KEY,
    pedido_id       BIGINT NOT NULL REFERENCES tb_pedido(id),
    produto_id      BIGINT NOT NULL, -- referencia logica a tb_produto.id; sem FK fisica (banco de outro servico)
    quantidade      INTEGER NOT NULL,
    preco_unitario  NUMERIC(12,2) NOT NULL -- snapshot no momento da criacao do pedido, imutavel
);
```

`produto_id` em `tb_saldo_estoque` e `tb_item_pedido` nunca é foreign key física — são bancos de serviços diferentes (database-per-service). A integridade é garantida pela ordem dos eventos (produto sempre existe antes de gerar saldo/pedido referenciando-o), não por constraint de banco.

## Contratos de Mensageria

### `product-changed`

Sempre carrega o snapshot completo do produto, independente do `event-type` (evita lógica de merge parcial no consumidor):

```json
{
  "eventId": "b3f1...",
  "occurredAt": "2026-09-12T14:00:00Z",
  "produtoId": 123,
  "sku": "ABC-001",
  "nome": "Produto X",
  "preco": 19.90,
  "ativo": true
}
```

Header AMQP: `event-type` = `product-created` | `product-updated` | `product-deactivated`.

### `reservation-requested`

```json
{
  "eventId": "c7a2...",
  "occurredAt": "2026-09-12T14:05:00Z",
  "pedidoId": 456,
  "itens": [
    { "produtoId": 123, "quantidade": 2 }
  ]
}
```

### `reservation-processed`

Resultado vai no **payload** (campo `resultado`), não em header — é dado de negócio do próprio evento, diferente do `event-type` de `product-changed` (que é metadado sobre o tipo de mutação):

```json
{
  "eventId": "d9e4...",
  "occurredAt": "2026-09-12T14:05:03Z",
  "pedidoId": 456,
  "resultado": "confirmed",
  "itensIndisponiveis": []
}
```

Em caso de rejeição, `resultado: "rejected"` e `itensIndisponiveis` lista os itens que não puderam ser reservados:

```json
{
  "eventId": "e1f5...",
  "occurredAt": "2026-09-12T14:05:03Z",
  "pedidoId": 456,
  "resultado": "rejected",
  "itensIndisponiveis": [
    { "produtoId": 123, "quantidadeSolicitada": 5, "quantidadeDisponivel": 2 }
  ]
}
```

## Contratos de API (REST)

Formato de erro padrão nos três serviços:

```json
{
  "codigo": "PRODUTO_NAO_ENCONTRADO",
  "mensagem": "Produto não encontrado ou inativo",
  "timestamp": "2026-09-12T14:00:00Z"
}
```

`ERRO_INTERNO` (500) é comum aos três serviços — qualquer exceção não mapeada explicitamente vira esse código, com mensagem genérica (não vaza stack trace nem detalhe interno na resposta):

```json
{
  "codigo": "ERRO_INTERNO",
  "mensagem": "Erro inesperado ao processar a requisição",
  "timestamp": "2026-09-12T14:00:00Z"
}
```

### produto-service

| Código | HTTP | Quando |
|---|---|---|
| `PRODUTO_NAO_ENCONTRADO` | 404 | Consulta/atualização/inativação de produtoId inexistente |
| `SKU_DUPLICADO` | 409 | Criação com SKU já cadastrado |
| `VALIDACAO` | 400 | Campo obrigatório ausente ou inválido (ex.: preço negativo) |
| `ERRO_INTERNO` | 500 | Falha não mapeada (ex.: banco indisponível) |

Sucesso (`POST /produtos`, 201):
```json
{ "id": 123, "nome": "Produto X", "descricao": "...", "sku": "ABC-001", "preco": 19.90, "ativo": true }
```

### pedido-service

| Código | HTTP | Quando |
|---|---|---|
| `PRODUTO_INDISPONIVEL` | 422 | Item do pedido referencia produtoId ausente ou inativo no cache local |
| `PEDIDO_NAO_ENCONTRADO` | 404 | Consulta de pedidoId inexistente |
| `VALIDACAO` | 400 | Quantidade zero/negativa ou lista de itens vazia |
| `ERRO_INTERNO` | 500 | Falha não mapeada (ex.: Redis indisponível) |

Sucesso (`POST /pedidos`, 201 — status inicial sempre `PENDENTE`, saga resolve assíncrono depois):
```json
{ "id": 456, "status": "PENDENTE", "itens": [ { "produtoId": 123, "quantidade": 2, "precoUnitario": 19.90 } ] }
```

Sucesso (`GET /pedidos/{id}`, 200 — após a saga resolver):
```json
{ "id": 456, "status": "CONFIRMADO", "itens": [ { "produtoId": 123, "quantidade": 2, "precoUnitario": 19.90 } ] }
```

### estoque-service

Sem API pública de reserva (isso é só via mensageria). Único endpoint REST é a entrada de estoque (Decisão 4):

| Código | HTTP | Quando |
|---|---|---|
| `PRODUTO_NAO_ENCONTRADO` | 404 | Entrada de estoque para produtoId sem `SaldoEstoque` criado ainda (produto não propagou via `product-changed`) |
| `VALIDACAO` | 400 | Quantidade de entrada zero/negativa |
| `ERRO_INTERNO` | 500 | Falha não mapeada (ex.: banco indisponível) |

## Risks / Trade-offs

| Risco | Mitigação / decisão |
|---|---|
| Mensagem duplicada (RabbitMQ é *at-least-once*) | Consumidores que alteram estado (ex.: Estoque reservando, Pedido atualizando status) devem ser idempotentes — verificar estado atual antes de aplicar, não assumir que o evento chega uma única vez. |
| Evento fora de ordem (ex.: `product-updated` antes de `product-created` chegar ao Pedido) | Incluir timestamp/versão no payload; consumidor ignora evento mais antigo que o estado local. |
| Fila com consumidor caído / mensagem envenenada | Fila `-delayed` (retry com backoff via TTL+DLX) por fila principal; após esgotar ciclos, mensagem vai para a fila `-failed` (DLQ definitiva, sem TTL) — ver `docs/taxonomia-filas-rabbitmq.md`. |
| Cache do Pedido desatualizado se um evento for perdido | Sem TTL no cache — é uma projeção, não cache-aside; entrada só muda por evento. Confiabilidade da entrega já é coberta pela resiliência de mensageria (`-delayed`/`-failed`). TTL foi cogitado e descartado: sem fallback síncrono no miss (decisão explícita), TTL só apagaria entradas válidas de produtos que não mudaram, causando rejeição indevida de pedido — risco pior que o que resolveria. |
| Rastrear um pedido através dos 3 serviços em produção | Fora do escopo desta fase (laboratório); se necessário depois, propor correlation-id/tracing como mudança separada. |

## Migration Plan

Não aplicável — greenfield, sem estado anterior para migrar. Ordem de implementação sugerida: `produto-service` primeiro (base do catálogo), depois `estoque-service` (consome `product-changed`), depois `pedido-service` (consome `product-changed` e `reservation-processed`, publica `reservation-requested`).
