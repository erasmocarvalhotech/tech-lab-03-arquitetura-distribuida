## Context

Os três serviços (`produto-service`, `estoque-service`, `pedido-service`) já têm tracing distribuído (change `observabilidade-tracing`): Micrometer Tracing + bridge OTel exportando pro Grafana Tempo, log console já correlacionado por `traceId`/`spanId`. O que falta é o log em si ter storage — hoje é só texto no stdout do processo `mvn spring-boot:run`, sem persistência, sem busca, sem filtro por serviço/nível/período.

Ambiente é um lab local: serviços Java rodam via `mvn spring-boot:run` direto no host (não em container — ver `README.md` de cada serviço), infra de apoio (Postgres, RabbitMQ, Redis, Tempo, Grafana) via `docker-compose.yml` na raiz.

## Goals / Non-Goals

**Goals:**
- Log dos 3 serviços persistido e consultável (filtro por serviço, texto, período) no Grafana.
- Pulo direto trace→log: a partir de um span no Tempo, ver os logs daquele `traceId` sem copiar/colar string manualmente.
- Manter o fluxo de desenvolvimento local igual (serviços continuam via `mvn spring-boot:run`, não em container).

**Non-Goals:**
- Métricas de negócio ou dashboards Prometheus/Mimir — não faz parte, mesmo Non-Goal já registrado no `observabilidade-tracing`.
- Mudar o formato/conteúdo do log — continua com o mesmo `logging.pattern.console` (inclui `traceId`/`spanId`), só ganha um destino a mais (arquivo).
- Retenção de log de longo prazo / storage externo (S3, GCS) — filesystem local basta pro lab, mesma decisão já tomada pro Tempo.
- Alertas sobre log (ex.: erro acima de X por minuto) — fora de escopo, só visualização/consulta.

## Decisions

### 1. Grafana Alloy (não Promtail) como agente coletor

**Por quê**: Promtail está em modo de manutenção — Grafana recomenda Alloy pra qualquer coleta nova (substituto oficial, mesmo fabricante do Loki/Tempo/Grafana já usados no projeto). Evita adotar algo já descontinuado no mesmo change que introduz a peça.

### 2. Coleta via arquivo de log + bind mount, não via log driver do Docker

**Alternativa considerada e rejeitada**: coletar stdout de containers Docker (abordagem mais comum em setups onde tudo já roda em container) — exigiria dockerizar os 3 serviços Java, quebrando o fluxo de desenvolvimento local atual (`mvn spring-boot:run`, hot-reload, debug via IDE). Custo desproporcional ao ganho, e o `README.md` de cada serviço já documenta esse fluxo.

**Decisão**: cada serviço ganha `logging.file.name` no `application.yml` apontando pra `./logs/<service>/app.log` (raiz do repo, `.gitignore`). O container do Alloy monta esse diretório via bind mount (`./logs:/var/log/techlab:ro`) e usa o componente `local.file_match` + `loki.source.file` pra tailar cada arquivo, atribuindo o label `service_name` a partir do nome do subdiretório. Serviços continuam rodando no host, sem Dockerfile novo.

### 3. Correlação trace→log via filtro de substring, sem mudar formato de log

**Por quê**: o `traceId` (32 chars hex) já aparece literal no log console (`[<traceId>-<spanId>] ...`, formato herdado da correlação automática do Spring Boot com Micrometer Tracing). Configurar `tracesToLogsV2` no datasource Tempo do Grafana com uma query LogQL `|= "<traceId>"` já basta — Grafana substitui `<traceId>` pelo valor do span clicado e roda a busca no Loki. Não precisa reformatar log pra JSON estruturado nem adicionar label `trace_id` por linha; o ganho (evitar reformatação, artefato mais simples) supera a perda (busca por substring é menos eficiente que campo indexado — irrelevante no volume de um lab).

### 4. Storage do Loki: filesystem local, mesma decisão do Tempo

**Por quê**: consistência com a decisão já tomada pro Tempo (change `observabilidade-tracing`, Risco 1) — sem objetivo de persistir histórico além da sessão de teste atual.

## Risks / Trade-offs

| Risco | Mitigação / decisão |
|---|---|
| Alloy (imagem `grafana/alloy`) pode ter as mesmas pegadinhas do Tempo (bind `0.0.0.0` vs `127.0.0.1`, imagem sem shell) — já vimos isso no `observabilidade-tracing` | Validar explicitamente na Etapa de infraestrutura antes de seguir pros 3 serviços, mesmo passo que already funcionou pro Tempo. |
| Correlação por substring (`|= "<traceId>"`) pode dar falso positivo se o `traceId` aparecer em outro contexto na mesma linha | Improvável — `traceId` é hex de 32 chars gerado aleatoriamente, colisão de substring é praticamente impossível no volume de um lab. |
| `logging.file.name` duplica log em disco (console + arquivo) — uso de disco cresce sem rotação configurada | Aceitável pro lab (sessões curtas); se necessário, `logging.logback.rollingpolicy.max-history` do Spring Boot já dá rotação pronta sem código custom. |
| Diretório `./logs/` sujo se o `.gitignore` não for atualizado junto | Task explícita de adicionar `logs/` ao `.gitignore` na Etapa de infraestrutura. |

## Migration Plan

Não aplicável — mudança aditiva (nova infraestrutura + config de log adicional), sem alteração de schema de banco, contrato de API ou payload de evento. Não afeta o `observabilidade-tracing` já implementado, só adiciona um datasource novo e aponta pro Tempo existente.
