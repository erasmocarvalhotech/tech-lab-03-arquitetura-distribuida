# pedido-service

Criação e consulta de pedidos. Mantém uma projeção local (Redis) dos dados de produto — nome, preço, status ativo — alimentada pelo stream `product-changed`, sem nenhuma chamada síncrona ao `produto-service`. Reserva de estoque via saga coreografada com o `estoque-service` (`reservation-requested` / `reservation-processed`).

Detalhes de arquitetura, decisões e contratos: [openspec/changes/arquitetura-microservicos/](../openspec/changes/arquitetura-microservicos/) (`design.md`, `specs/cadastro-pedido/spec.md`) e [docs/taxonomia-filas-rabbitmq.md](../docs/taxonomia-filas-rabbitmq.md).

## Pré-requisitos

- Java 21
- Maven 3.9+
- Docker + Docker Compose (para subir PostgreSQL, RabbitMQ e Redis)

## Subir a infraestrutura

A partir da raiz do repositório (não desta pasta):

```bash
docker compose up -d postgres-produto postgres-pedido rabbitmq redis
```

`postgres-produto` também é necessário aqui porque os exemplos abaixo dependem do `produto-service` rodando junto (é ele quem publica o `product-changed` que alimenta o cache local de produto). Isso sobe:
- PostgreSQL (`db_produto`) na porta `5432`
- PostgreSQL (`db_pedido`) na porta `5434`
- RabbitMQ (com management plugin) nas portas `5672` (AMQP) e `15672` (painel web, usuário/senha padrão `techlab`/`techlab`)
- Redis na porta `6379`

A topologia de exchanges/filas (`mb-techlab-order-queue-product-changed`, `mb-techlab-order-exchange-topic-reservation-requested`, `mb-techlab-order-queue-reservation-processed` e os pares `-delayed`/`-failed`) já é declarada via `infra/rabbitmq/definitions.json`, carregada automaticamente na subida do container.

Aguarde os serviços ficarem `healthy`:

```bash
docker compose ps
```

## Rodar o pedido-service

A partir desta pasta (`pedido-service/`):

```bash
mvn spring-boot:run
```

A aplicação sobe em `http://localhost:8083`. As migrations do Flyway (criação de `tb_pedido`/`tb_item_pedido`) rodam automaticamente na inicialização.

Para os exemplos abaixo funcionarem, suba também o `produto-service` (em outro terminal, a partir de `produto-service/`):

```bash
mvn spring-boot:run
```

Ele sobe em `http://localhost:8081` — ver [produto-service/README.md](../produto-service/README.md).

### Variáveis de ambiente (opcionais)

Todas têm default compatível com o `docker-compose.yml` da raiz — só precisa setar se você mudou usuário/senha/host lá:

| Variável | Default |
|---|---|
| `POSTGRES_USER` | `techlab` |
| `POSTGRES_PASSWORD` | `techlab` |
| `RABBITMQ_HOST` | `localhost` |
| `RABBITMQ_PORT` | `5672` |
| `RABBITMQ_DEFAULT_USER` | `techlab` |
| `RABBITMQ_DEFAULT_PASS` | `techlab` |
| `REDIS_HOST` | `localhost` |
| `REDIS_PORT` | `6379` |

## Testar a API

Exemplos abaixo em bash (Git Bash / Linux / macOS). Evite acento (ç, ã, ...) dentro do JSON passado direto no terminal — no Git Bash/console do Windows o argv nem sempre chega ao `curl` como UTF-8, e o Jackson rejeita a sequência de bytes malformada (`Invalid UTF-8 middle byte`). Se precisar de acento, mande num arquivo (`curl -d @arquivo.json`, arquivo salvo como UTF-8) em vez de inline.

Pedido não aceita item para um produto que nunca existiu no catálogo — o fluxo completo passa pelo `produto-service` primeiro:

Criar produto no `produto-service` (propaga para o cache local aqui via `product-changed`):

```bash
curl -X POST http://localhost:8081/produtos \
  -H "Content-Type: application/json" \
  -d '{"nome":"Produto X","descricao":"Descricao","sku":"ABC-001","preco":19.90}'
```

Guarde o `id` retornado (ex.: `1`). A propagação do evento é assíncrona — dê um instante antes do próximo passo, ou confirme no painel do RabbitMQ (`http://localhost:15672`) que a mensagem já foi consumida.

Criar pedido (usa o preço do cache local no momento da criação):

```bash
curl -X POST http://localhost:8083/pedidos \
  -H "Content-Type: application/json" \
  -d '{"itens":[{"produtoId":1,"quantidade":2}]}'
```

O pedido nasce com status `PENDENTE` e publica `reservation-requested` para o `estoque-service`. Consulte novamente para ver o resultado da saga (`CONFIRMADO` ou `REJEITADO_SEM_ESTOQUE`, assíncrono):

```bash
curl http://localhost:8083/pedidos/1
```

## Fluxo assíncrono da saga

1. `produto-service` publica `product-changed` → `pedido-service` consome e mantém a projeção local em Redis (upsert em `product-created`/`product-updated`, remoção em `product-deactivated`).
2. `pedido-service` cria o pedido lendo preço/ativo exclusivamente do cache local e publica `reservation-requested` após o commit.
3. `estoque-service` consome, tenta reservar e publica `reservation-processed` com `resultado=confirmed` ou `resultado=rejected`.
4. `pedido-service` consome `reservation-processed` e atualiza o status do pedido (idempotente — só aplica a transição se ainda estiver `PENDENTE`).

Acompanhe as filas em `http://localhost:15672`.

## Rodar os testes

```bash
mvn test
```

## Endpoints e códigos de erro

Ver contratos completos em [design.md](../openspec/changes/arquitetura-microservicos/design.md#contratos-de-api-rest). Resumo:

| Método | Rota | Descrição |
|---|---|---|
| `POST` | `/pedidos` | Cria pedido (201, status inicial `PENDENTE`) |
| `GET` | `/pedidos/{id}` | Consulta pedido por id |

Erros: `PRODUTO_INDISPONIVEL` (422, produto ausente ou inativo no cache local), `PEDIDO_NAO_ENCONTRADO` (404), `VALIDACAO` (400), `ERRO_INTERNO` (500).
