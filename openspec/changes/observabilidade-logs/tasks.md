## 1. Infraestrutura

- [ ] 1.1 Adicionar `logs/` ao `.gitignore` (raiz do repo)
- [ ] 1.2 Adicionar serviço `loki` ao `docker-compose.yml` (imagem `grafana/loki`, porta 3100, storage filesystem local, config em `infra/loki/loki.yaml`)
- [ ] 1.3 Adicionar serviço `alloy` ao `docker-compose.yml` (imagem `grafana/alloy`, bind mount `./logs:/var/log/techlab:ro`, config em `infra/alloy/config.alloy`), apontando o output pro `loki` do passo 1.2
- [ ] 1.4 Validar que Alloy sobe e não fica preso em bind de loopback (mesma pegadinha do Tempo no `observabilidade-tracing` — checar se a imagem tem shell pra healthcheck ou se precisa remover)
- [ ] 1.5 Adicionar datasource `Loki` em `infra/grafana/provisioning/datasources/` apontando pro `loki:3100`
- [ ] 1.6 Configurar `tracesToLogsV2` no datasource `Tempo` (`infra/grafana/provisioning/datasources/tempo.yaml`) apontando pro datasource Loki, query `|= "${__span.traceId}"` (ou equivalente da versão instalada)

## 2. produto-service

- [ ] 2.1 Configurar `logging.file.name=./logs/produto-service/app.log` no `application.yml` (sem alterar `logging.pattern.console`)
- [ ] 2.2 Validar que o arquivo é criado e recebe as mesmas linhas do console ao processar uma requisição

## 3. estoque-service

- [ ] 3.1 Configurar `logging.file.name=./logs/estoque-service/app.log` no `application.yml`
- [ ] 3.2 Validar que o arquivo é criado e recebe as mesmas linhas do console ao consumir uma mensagem

## 4. pedido-service

- [ ] 4.1 Configurar `logging.file.name=./logs/pedido-service/app.log` no `application.yml`
- [ ] 4.2 Validar que o arquivo é criado e recebe as mesmas linhas do console ao processar uma requisição

## 5. Validação end-to-end

- [ ] 5.1 Subir os 3 serviços (via `mvn spring-boot:run`, sem mudança no fluxo) + infraestrutura (incluindo `loki` e `alloy`) via docker-compose
- [ ] 5.2 No Grafana Explore, datasource Loki, filtrar por `service_name="produto-service"` e confirmar que aparecem as linhas esperadas
- [ ] 5.3 Criar um produto, pegar o `traceId` no Tempo, clicar na ação de ver logs do span e confirmar que o Grafana abre a consulta no Loki já filtrada, retornando log dos serviços envolvidos naquele trace (fanout `produto-service` → `estoque-service`/`pedido-service`)
- [ ] 5.4 Atualizar `docs/guia-grafana-tempo.md` (ou criar `docs/guia-grafana-loki.md`) com o passo a passo de consulta de log e uso do trace-to-logs
