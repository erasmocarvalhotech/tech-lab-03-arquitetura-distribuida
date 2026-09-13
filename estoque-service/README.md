# estoque-service

Controle de saldo de estoque por produto (disponível/reservado). Não conhece nome, preço ou SKU — sincroniza a existência do produto consumindo o stream `product-changed` e é o único dono de quantidade. Reserva de estoque via lock otimista (`@Version`), processada de forma assíncrona pelo stream `reservation-requested`/`reservation-processed`.

Detalhes de arquitetura, decisões e contratos: [openspec/changes/arquitetura-microservicos/](../openspec/changes/arquitetura-microservicos/) (`design.md`, `specs/controle-estoque/spec.md`) e [docs/taxonomia-filas-rabbitmq.md](../docs/taxonomia-filas-rabbitmq.md).

## Pré-requisitos

- Java 21
- Maven 3.9+
- Docker + Docker Compose (para subir PostgreSQL e RabbitMQ)

Se o `java -version`/`mvn -version` do terminal cair no JDK errado (ex.: outro Java já em `PATH`), aponte o terminal PowerShell atual para o JDK 21 antes de rodar `mvn` (vale só para a sessão aberta do terminal):

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21.0.11"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
```

## Subir a infraestrutura

A partir da raiz do repositório (não desta pasta):

```bash
docker compose up -d postgres-produto postgres-estoque rabbitmq
```

`postgres-produto` também é necessário aqui porque os exemplos abaixo dependem do `produto-service` rodando junto (é ele quem publica o `product-changed` que faz o `estoque-service` criar o saldo do produto). Isso sobe:
- PostgreSQL (`db_produto`) na porta `5432`
- PostgreSQL (`db_estoque`) na porta `5436`
- RabbitMQ (com management plugin) nas portas `5672` (AMQP) e `15672` (painel web, usuário/senha padrão `techlab`/`techlab`)

A topologia de exchanges/filas (`mb-techlab-stock-queue-product-changed`, `mb-techlab-stock-queue-reservation-requested`, `mb-techlab-stock-exchange-topic-reservation-processed` e os pares `-delayed`/`-failed`) já é declarada via `infra/rabbitmq/definitions.json`, carregada automaticamente na subida do container.

Aguarde os serviços ficarem `healthy`:

```bash
docker compose ps
```

## Rodar o estoque-service

A partir desta pasta (`estoque-service/`):

```bash
mvn spring-boot:run
```

A aplicação sobe em `http://localhost:8082`. As migrations do Flyway (criação de `tb_saldo_estoque`) rodam automaticamente na inicialização.

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

## Testar a API

Exemplos abaixo em bash (Git Bash / Linux / macOS). Evite acento (ç, ã, ...) dentro do JSON passado direto no terminal — no Git Bash/console do Windows o argv nem sempre chega ao `curl` como UTF-8, e o Jackson rejeita a sequência de bytes malformada (`Invalid UTF-8 middle byte`). Se precisar de acento, mande num arquivo (`curl -d @arquivo.json`, arquivo salvo como UTF-8) em vez de inline.

Estoque não tem saldo pra um produto que nunca existiu no catálogo — o fluxo completo passa pelo `produto-service` primeiro:

Criar produto no `produto-service` (gera o saldo zerado aqui via `product-changed`):

```bash
curl -X POST http://localhost:8081/produtos \
  -H "Content-Type: application/json" \
  -d '{"nome":"Produto X","descricao":"Descricao","sku":"ABC-001","preco":19.90}'
```

Guarde o `id` retornado (ex.: `1`). A propagação do evento é assíncrona — dê um instante antes do próximo passo, ou confirme no painel do RabbitMQ (`http://localhost:15672`) que a mensagem já foi consumida.

Consultar o saldo criado (deve vir zerado):

```bash
curl http://localhost:8082/estoque/1
```

Registrar entrada de estoque (carga inicial ou reposição):

```bash
curl -X POST http://localhost:8082/estoque/entradas \
  -H "Content-Type: application/json" \
  -d '{"produtoId":1,"quantidade":50}'
```

Consultar saldo novamente (deve refletir a entrada):

```bash
curl http://localhost:8082/estoque/1
```

## Fluxo assíncrono (sem API própria de reserva)

Não existe endpoint REST de reserva — a reserva de estoque é sempre via mensageria, dentro da saga coreografada com o `pedido-service`:

1. `produto-service` publica `product-changed` (header `event-type=product-created`) → `estoque-service` consome e cria `SaldoEstoque` zerado para o produto.
2. `pedido-service` publica `reservation-requested` → `estoque-service` consome, valida disponibilidade com lock otimista (`@Version`) e publica `reservation-processed` com `resultado=confirmed` ou `resultado=rejected`.
3. Conflito de concorrência (`OptimisticLockingFailureException`) não publica nenhum resultado nessa tentativa — a mensagem recicla pela fila `-delayed` (até 3 tentativas no total, depois `-failed`). Estoque insuficiente é resultado de negócio, publicado imediatamente sem retry.

Acompanhe as filas em `http://localhost:15672`.

## Rodar os testes

```bash
mvn test
```

## Endpoints e códigos de erro

Ver contratos completos em [design.md](../openspec/changes/arquitetura-microservicos/design.md#contratos-de-api-rest). Resumo:

| Método | Rota | Descrição |
|---|---|---|
| `POST` | `/estoque/entradas` | Registra entrada de estoque (carga inicial/reposição), 201 |
| `GET` | `/estoque/{produtoId}` | Consulta saldo disponível/reservado por produto |

Erros: `PRODUTO_NAO_ENCONTRADO` (404), `VALIDACAO` (400), `ERRO_INTERNO` (500).
