# Guia: traces e logs no Grafana

Contexto e decisões completas em [openspec/changes/archive/2026-09-13-observabilidade-tracing/](../openspec/changes/archive/2026-09-13-observabilidade-tracing/) e [openspec/changes/observabilidade-logs/](../openspec/changes/observabilidade-logs/) (`design.md`, `tasks.md` de cada um).

## O que tem aqui

- **Traces**: Grafana Explore com datasource **Tempo**, alimentado via OTLP pelos 3 serviços (Micrometer Tracing + bridge OTel).
- **Logs centralizados**: Grafana Explore com datasource **Loki**, alimentado pelo **Grafana Alloy** — cada serviço escreve log em arquivo local (`./logs/<service>/app.log`, além do console), Alloy tail nesses arquivos via bind mount e envia pro Loki. Serviços continuam rodando via `mvn spring-boot:run`, não em container.
- **Trace-to-logs**: a partir de um span no Tempo, um clique pula direto pros logs daquele `traceId` no Loki — sem copiar/colar string manualmente.

## Acessar

1. Subir a infra: `docker compose up -d` (sobe, entre outros, `tempo`, `loki`, `alloy` e `grafana`).
2. Abrir `http://localhost:3000`. Login anônimo habilitado (`GF_AUTH_ANONYMOUS_ENABLED=true`) — não pede usuário/senha.
3. Menu lateral → **Explore**.

## Ver os traces (datasource Tempo)

Selecionar datasource **Tempo** no dropdown do topo. Na aba **TraceQL** (padrão):

- Campo de query vazio não retorna nada (`No data`). Digite `{}` e `Shift+Enter` — lista os últimos traces (todos os serviços).
- Pra achar um trace específico: cola só o `traceId` (32 chars hex, **sem aspas e sem o `-spanId`**) no campo e `Shift+Enter`. Ex.: `12431a3a3e912c9eab9939f6cd0cb8c9` — não `"12431a3a...-21c5d809..."` (isso é o bracket inteiro do log, formato errado pra essa busca).
- Alternativa mais simples: aba **Search** (ao lado de TraceQL) — filtro por dropdown de `Service Name`, sem precisar saber sintaxe TraceQL.

O resultado é uma tabela (Trace ID, horário, serviço, nome da operação, duração). Clicar numa linha abre o **waterfall**: árvore de spans com duração de cada etapa, atravessando os serviços.

## Ver os logs (datasource Loki)

Trocar o datasource pra **Loki** no dropdown do topo. Query LogQL — sempre precisa de um seletor de stream entre chaves antes de qualquer filtro:

- Todos os serviços: `{service_name=~".+"}`
- Um serviço específico: `{service_name="produto-service"}`
- Filtrando texto dentro do resultado: `{service_name="produto-service"} |= "produtoId=27"` (o `|=` sozinho sem o `{...}` na frente dá erro de parse)

## Pular de um trace pros logs (trace-to-logs)

1. No Tempo, abre um trace (waterfall).
2. Clica num span — aparece um botão **"Logs for this trace"** (ou "for this span") nos detalhes do span.
3. Abre um painel novo com o datasource Loki já preenchido, query `{service_name=~".+"} |= "<traceId>"` — log de todos os serviços que participaram daquele trace, na mesma tela.

Cada linha de log mostra o bracket `[<traceId>-<spanId>]`: os 32 chars hex antes do traço são o `traceId` (igual em todo serviço do mesmo trace), os 16 chars depois são o `spanId` (próprio de cada span — diferente em cada serviço, e diferente a cada nova tentativa de entrega numa retentativa de mensagem).

## Cenários pra explorar

- **Fanout de produto**: `POST /produtos` no `produto-service` → trace com spans em `produto-service`, `estoque-service` e `pedido-service` (os dois consomem `product-changed` em paralelo); trace-to-logs traz log dos 3.
- **Saga de reserva**: `POST /pedidos` no `pedido-service` → trace com spans `pedido-service → estoque-service → pedido-service` (`reservation-requested` / `reservation-processed`).
- **Falha/retry**: parar um serviço dependente (ex.: Redis) e criar um produto — a mensagem falha, recicla via fila `-delayed`, e o mesmo `traceId` aparece em múltiplas tentativas de entrega (spans e linhas de log diferentes, mesmo trace).
