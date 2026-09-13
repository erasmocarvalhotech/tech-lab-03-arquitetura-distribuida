# produto-service

Cadastro e consulta de produtos (catálogo — nome, descrição, SKU, preço). Nunca exclui um produto fisicamente, só inativa (`ativo=false`). Publica o stream de eventos `product-changed` no RabbitMQ após cada criação, atualização ou inativação.

Detalhes de arquitetura, decisões e contratos: [openspec/changes/arquitetura-microservicos/](../openspec/changes/arquitetura-microservicos/) (`design.md`, `specs/cadastro-produto/spec.md`) e [docs/taxonomia-filas-rabbitmq.md](../docs/taxonomia-filas-rabbitmq.md).

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
docker compose up -d postgres-produto rabbitmq
```

Isso sobe:
- PostgreSQL (`db_produto`) na porta `5432`
- RabbitMQ (com management plugin) nas portas `5672` (AMQP) e `15672` (painel web, usuário/senha padrão `techlab`/`techlab`)

A topologia de exchanges/filas (`mb-techlab-product-exchange-topic-product-changed` e as filas de Estoque/Pedido que consomem esse stream) já é declarada via `infra/rabbitmq/definitions.json`, carregada automaticamente na subida do container.

Aguarde os dois serviços ficarem `healthy`:

```bash
docker compose ps
```

## Rodar o produto-service

A partir desta pasta (`produto-service/`):

```bash
mvn spring-boot:run
```

A aplicação sobe em `http://localhost:8081`. As migrations do Flyway (criação de `tb_produto`) rodam automaticamente na inicialização.

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

Criar produto:

```bash
curl -X POST http://localhost:8081/produtos \
  -H "Content-Type: application/json" \
  -d '{"nome":"Produto X","descricao":"Descricao","sku":"ABC-001","preco":19.90}'
```

Consultar por id:

```bash
curl http://localhost:8081/produtos/1
```

Listar todos:

```bash
curl http://localhost:8081/produtos
```

Atualizar:

```bash
curl -X PUT http://localhost:8081/produtos/1 \
  -H "Content-Type: application/json" \
  -d '{"nome":"Produto X","descricao":"Descricao nova","preco":24.90}'
```

Inativar (nunca exclui a linha):

```bash
curl -X DELETE http://localhost:8081/produtos/1
```

Cada criação/atualização/inativação publica um evento no exchange `mb-techlab-product-exchange-topic-product-changed` com o header `event-type` (`product-created`/`product-updated`/`product-deactivated`) — visível no painel do RabbitMQ em `http://localhost:15672`.

## Rodar os testes

```bash
mvn test
```

## Endpoints e códigos de erro

Ver contratos completos em [design.md](../openspec/changes/arquitetura-microservicos/design.md#contratos-de-api-rest). Resumo:

| Método | Rota | Descrição |
|---|---|---|
| `POST` | `/produtos` | Cria produto (201) |
| `GET` | `/produtos` | Lista produtos |
| `GET` | `/produtos/{id}` | Consulta produto por id |
| `PUT` | `/produtos/{id}` | Atualiza nome/descrição/preço |
| `DELETE` | `/produtos/{id}` | Inativa produto (204, `ativo=false`, sem exclusão física) |

Erros: `PRODUTO_NAO_ENCONTRADO` (404), `SKU_DUPLICADO` (409), `VALIDACAO` (400), `ERRO_INTERNO` (500).
