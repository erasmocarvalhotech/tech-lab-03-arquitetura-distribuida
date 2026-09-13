## 1. Infraestrutura

- [x] 1.1 Adicionar `logs/` ao `.gitignore` (raiz do repo)
- [x] 1.2 Adicionar serviço `loki` ao `docker-compose.yml` (imagem `grafana/loki`, porta 3100, storage filesystem local, config em `infra/loki/loki.yaml`)
- [x] 1.3 Adicionar serviço `alloy` ao `docker-compose.yml` (imagem `grafana/alloy`, bind mount `./logs:/var/log/techlab:ro`, config em `infra/alloy/config.alloy`), apontando o output pro `loki` do passo 1.2
- [x] 1.4 Validar que Alloy sobe e não fica preso em bind de loopback — bind explícito `0.0.0.0:12345` no comando (`--server.http.listen-addr`), sem a pegadinha do Tempo
- [x] 1.5 Adicionar datasource `Loki` em `infra/grafana/provisioning/datasources/` apontando pro `loki:3100` — precisou ficar no mesmo arquivo do Tempo (`tempo.yaml`), Grafana falhou ao provisionar com `datasourceUid` referenciando datasource de outro arquivo (`data source not found`); após falha, também foi preciso `--force-recreate` do container (não só `restart`) pra limpar o estado interno corrompido pela tentativa anterior
- [x] 1.6 Configurar `tracesToLogsV2` no datasource `Tempo` (`infra/grafana/provisioning/datasources/tempo.yaml`) apontando pro datasource Loki — duas pegadinhas: (1) precisou escapar `${__span.traceId}` como `$${__span.traceId}` (duplo `$`), porque o provisioning do Grafana faz expansão de variável de ambiente em `${VAR}` e zerava o template sem o escape; (2) a query customizada precisa de seletor de stream LogQL na frente (`{service_name=~".+"}`), um `|= "..."` sozinho não é LogQL válido — query final: `{service_name=~".+"} |= "$${__span.traceId}"`

## 2. produto-service

- [x] 2.1 Configurar `logging.file.name` no `application.yml` (sem alterar `logging.pattern.console`) — path relativo `../logs/produto-service/app.log`, porque o `mvn spring-boot:run` roda com working directory dentro da própria pasta do serviço, não na raiz do repo (onde o bind mount do Alloy aponta)
- [x] 2.2 Validar que o arquivo é criado e recebe as mesmas linhas do console ao processar uma requisição

## 3. estoque-service

- [x] 3.1 Configurar `logging.file.name` no `application.yml` — `../logs/estoque-service/app.log`
- [x] 3.2 Validar que o arquivo é criado e recebe as mesmas linhas do console ao consumir uma mensagem

## 4. pedido-service

- [x] 4.1 Configurar `logging.file.name` no `application.yml` — `../logs/pedido-service/app.log`
- [x] 4.2 Validar que o arquivo é criado e recebe as mesmas linhas do console ao processar uma requisição

## 5. Validação end-to-end

- [x] 5.1 Subir os 3 serviços (via `mvn spring-boot:run`, sem mudança no fluxo) + infraestrutura (incluindo `loki` e `alloy`) via docker-compose
- [x] 5.2 No Grafana Explore, datasource Loki, filtrar por `service_name="produto-service"` e confirmar que aparecem as linhas esperadas
- [x] 5.3 Criar um produto, pegar o `traceId` no Tempo, clicar na ação de ver logs do span e confirmar que o Grafana abre a consulta no Loki já filtrada, retornando log dos serviços envolvidos naquele trace (fanout `produto-service` → `estoque-service`/`pedido-service`)
- [x] 5.4 Atualizar guia de uso do Grafana — renomeado `docs/guia-grafana-tempo.md` para `docs/guia-grafana.md` (não é mais só sobre Tempo), com seções de Loki e trace-to-logs
