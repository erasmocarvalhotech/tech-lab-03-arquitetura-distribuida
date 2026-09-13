## Why

O change `observabilidade-tracing` deu aos três serviços tracing distribuído (Tempo) — dá pra ver o caminho e a duração de uma transação. Mas o log de texto (`INFO Produto criado: id=24 sku=...`) continua sem storage nenhum: vive só no stdout do processo `mvn spring-boot:run`, some no restart, e correlacionar com um trace é manual (copiar o `traceId` do Tempo e procurar essa string no console de cada serviço, um por um). Isso não escala nem pra debug local com os 3 serviços rodando ao mesmo tempo.

## What Changes

- Adiciona **Grafana Loki** como backend de log centralizado, com **Grafana Alloy** coletando log dos 3 serviços e enviando pro Loki.
- Cada serviço passa a escrever log também em arquivo (`logging.file.name`, além do console — nada muda no console), num diretório host compartilhado (`./logs/<service>/`). Alloy roda em container, lê esses arquivos via bind mount, sem exigir que os serviços rodem em container — continuam via `mvn spring-boot:run` como hoje.
- Adiciona datasource Loki no Grafana (`infra/grafana/provisioning/datasources/`), com **trace-to-logs** configurado a partir do datasource Tempo já existente (clicar um span no Tempo pula direto pros logs daquele `traceId` no Loki).

## Capabilities

### New Capabilities
- `observabilidade-logs`: logs dos três serviços centralizados no Loki, consultáveis e filtráveis no Grafana, com pulo direto trace→log a partir de um span no Tempo.

### Modified Capabilities
(nenhuma — não muda formato nem conteúdo do log em si, só onde ele é coletado e armazenado)

## Impact

- Novos containers `loki` e `alloy` no `docker-compose.yml`, com config em `infra/loki/` e `infra/alloy/`.
- Cada `application.yml` ganha `logging.file.name` apontando pra `./logs/<service>/app.log` (diretório novo na raiz do repo, ignorado no `.gitignore`).
- Datasource Loki novo no Grafana, com trace-to-logs apontando pro datasource Tempo existente.
- Fluxo de desenvolvimento local não muda — serviços continuam subindo via `mvn spring-boot:run`, só passam a também escrever em arquivo além do console.
- Sem mudança de contrato de API, payload de evento ou formato de log (`logging.pattern.console` continua o mesmo).
