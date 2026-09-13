# Guia: acessando traces no Grafana

Contexto e decisões completas em [openspec/changes/observabilidade-tracing/](../openspec/changes/observabilidade-tracing/) (`design.md`, `tasks.md`).

## O que tem aqui (e o que não tem)

- **Traces**: sim — Grafana Explore com datasource **Tempo**, alimentado via OTLP pelos 3 serviços (Micrometer Tracing + bridge OTel).
- **Logs centralizados**: não — não há Loki configurado neste lab. Log de cada serviço fica só no console/terminal onde ele roda (`mvn spring-boot:run`). A correlação log↔trace é manual: o `traceId` aparece tanto no console (entre colchetes, formato `traceId-spanId`) quanto no Tempo — basta comparar a string.

## Acessar

1. Subir a infra: `docker compose up -d` (sobe, entre outros, `tempo` e `grafana`).
2. Abrir `http://localhost:3000`. Login anônimo habilitado (`GF_AUTH_ANONYMOUS_ENABLED=true`) — não pede usuário/senha.
3. Menu lateral → **Explore**. Datasource **Tempo** já vem selecionado (é o único configurado, provisionado automaticamente).

## Ver os traces

Na aba que abre por padrão (**TraceQL**):

- Campo de query vazio não retorna nada (`No data`). Digite `{}` e `Shift+Enter` — lista os últimos traces (todos os serviços).
- Pra achar um trace específico: cola o `traceId` direto no campo (ex.: `c2d402b177aeb64a2d3a5e6f16203ebd`) e `Shift+Enter`.
- Alternativa mais simples: aba **Search** (ao lado de TraceQL) — filtro por dropdown de `Service Name`, sem precisar saber sintaxe TraceQL.

O resultado é uma tabela (Trace ID, horário, serviço, nome da operação, duração). Clicar numa linha abre o **waterfall**: árvore de spans com duração de cada etapa, atravessando os serviços.

## Correlacionar com log

1. No waterfall do Tempo, copia o `Trace ID` (aparece no topo da página do trace).
2. Procura essa mesma string entre colchetes no console de cada serviço — formato `[<traceId>-<spanId>]`, logo antes do nome da classe que logou.

Exemplo (fanout de `product-changed`, 1 requisição HTTP gerando consumo em 2 serviços):

```
produto-service: [c2d402b177aeb64a2d3a5e6f16203ebd-a8f1bdf02bd01729] ProdutoService : Produto criado: id=24 sku=TEN-1381
estoque-service: [c2d402b177aeb64a2d3a5e6f16203ebd-d6219fe64bafd05c] ProductChangedListener : Saldo de estoque criado: produtoId=24 sku=TEN-1381
```

Mesmo `traceId` (32 chars hex) nos dois, `spanId` (16 chars hex) diferente — span filho no consumidor.

## Cenários pra explorar

- **Fanout de produto**: `POST /produtos` no `produto-service` → trace com spans em `produto-service`, `estoque-service` e `pedido-service` (os dois consomem `product-changed` em paralelo).
- **Saga de reserva**: `POST /pedidos` no `pedido-service` → trace com spans `pedido-service → estoque-service → pedido-service` (`reservation-requested` / `reservation-processed`).

## Se precisar de logs centralizados no Grafana

Não faz parte deste change. Precisaria adicionar Loki + agente de coleta (Promtail/Alloy) + datasource no Grafana com trace-to-logs configurado — escopo de um novo change OpenSpec, não deste.
