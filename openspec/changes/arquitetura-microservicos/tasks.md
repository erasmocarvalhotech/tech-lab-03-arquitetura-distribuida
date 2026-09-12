## 1. Infraestrutura local

- [x] 1.1 Criar `docker-compose.yml` com 3 instâncias PostgreSQL (db_produto, db_estoque, db_pedido), 1 RabbitMQ (com management plugin) e 1 Redis
- [x] 1.2 Declarar exchange `mb-techlab-product-exchange-topic-product-changed` (routing key `product-changed`) e as filas `mb-techlab-stock-queue-product-changed` e `mb-techlab-order-queue-product-changed`, cada uma com par `-delayed`/`-failed` (ver `docs/taxonomia-filas-rabbitmq.md`)
- [x] 1.3 Declarar exchange `mb-techlab-order-exchange-topic-reservation-requested` (routing key `reservation-requested`) e a fila `mb-techlab-stock-queue-reservation-requested` com par `-delayed`/`-failed`
- [x] 1.4 Declarar exchange `mb-techlab-stock-exchange-topic-reservation-processed` (routing key `reservation-processed`) e a fila `mb-techlab-order-queue-reservation-processed` com par `-delayed`/`-failed`
- [x] 1.5 Para cada fila `-delayed`: configurar `x-message-ttl=60000` (1 min) + `x-dead-letter-exchange=""` + `x-dead-letter-routing-key` = nome da fila principal correspondente

## 2. produto-service

- [ ] 2.1 Scaffold do projeto Spring Boot (Java 21), pacotes `controller`, `service`, `repository`, `entity`, `dto`, `messaging`
- [ ] 2.2 Entity `Produto` (`@Table(name = "tb_produto")`; nome, descrição, sku, preço, ativo) + `ProdutoRepository` (Spring Data JPA sobre `db_produto`)
- [ ] 2.3 `ProdutoService` com criação, atualização, consulta e inativação (nunca exclusão física — `DELETE /produtos/{id}` só marca `ativo=false`)
- [ ] 2.4 `ProdutoController` REST (`POST/GET/PUT/DELETE /produtos`)
- [ ] 2.5 `ProdutoEventPublisher` — publica no exchange `mb-techlab-product-exchange-topic-product-changed` (routing key `product-changed`) logo após o commit, com header `event-type` = `product-created`/`product-updated`/`product-deactivated`
- [ ] 2.6 Testes: criação publica evento com header `event-type=product-created` correto; payload não contém campo de quantidade

## 3. estoque-service

- [ ] 3.1 Scaffold do projeto Spring Boot (Java 21), mesmos pacotes padrão
- [ ] 3.2 Entity `SaldoEstoque` (`@Table(name = "tb_saldo_estoque")`; produtoId, quantidadeDisponivel, quantidadeReservada, `@Version`) + `SaldoEstoqueRepository` sobre `db_estoque`
- [ ] 3.3 Listener consumindo `mb-techlab-stock-queue-product-changed` — cria `SaldoEstoque` zerado quando header `event-type=product-created`
- [ ] 3.4 Endpoint/ação de entrada de estoque (carga inicial e reposição), separada do fluxo de criação de produto
- [ ] 3.5 `SaldoEstoqueService.reservar` — valida `quantidadeDisponivel >= quantidade` em memória e salva via JPA (lock otimista pelo `@Version`)
- [ ] 3.6 Listener consumindo `mb-techlab-stock-queue-reservation-requested` — em `OptimisticLockException`, não faz ack e deixa a mensagem seguir pro fluxo `-delayed` (sem tratar como resposta de negócio); em estoque insuficiente, trata como resultado de negócio (não é erro, não aciona retry)
- [ ] 3.7 `EstoqueEventPublisher` — publica no exchange `mb-techlab-stock-exchange-topic-reservation-processed` (routing key `reservation-processed`) após o commit, com header/campo indicando `confirmed` ou `rejected`
- [ ] 3.8 Testes: reserva bem-sucedida decrementa saldo; reserva com saldo insuficiente (sem conflito) publica `rejected` sem retry; reserva com conflito de concorrência (`OptimisticLockException`) não publica nada e a mensagem é reprocessada via `-delayed`

## 4. pedido-service

- [ ] 4.1 Scaffold do projeto Spring Boot (Java 21), mesmos pacotes padrão + `cache`
- [ ] 4.2 Entities `Pedido` (`@Table(name = "tb_pedido")`; status) e `ItemPedido` (`@Table(name = "tb_item_pedido")`; produtoId, quantidade, precoUnitario snapshot) + repositories sobre `db_pedido`
- [ ] 4.3 Listener consumindo `mb-techlab-order-queue-product-changed` — upsert no cache Redis local (nome/preço/ativo, sem TTL — projeção mantida só por evento) para `event-type` created/updated; remove a chave do cache (nunca o produto em si, que não existe nesse serviço) para `product-deactivated`
- [ ] 4.4 `PedidoService.criarPedido` — lê preço/ativo exclusivamente do cache local; rejeita se produto ausente ou inativo no cache
- [ ] 4.5 `PedidoEventPublisher` — publica no exchange `mb-techlab-order-exchange-topic-reservation-requested` (routing key `reservation-requested`) após commit da criação do pedido
- [ ] 4.6 Listener consumindo `mb-techlab-order-queue-reservation-processed` — atualiza status para `CONFIRMADO` (confirmed) ou `REJEITADO_SEM_ESTOQUE` (rejected)
- [ ] 4.7 `PedidoController` REST (`POST /pedidos`, `GET /pedidos/{id}`)
- [ ] 4.8 Testes: criação de pedido usa preço do cache; rejeição por produto ausente no cache; confirmação e rejeição via `reservation-processed`

## 5. Teste de integração ponta a ponta

- [ ] 5.1 Subir os 3 serviços + infraestrutura via docker-compose
- [ ] 5.2 Fluxo feliz: criar produto → aguardar propagação do cache no Pedido → dar entrada de estoque → criar pedido → verificar status `CONFIRMADO`
- [ ] 5.3 Fluxo de rejeição: criar pedido sem estoque disponível → verificar status `REJEITADO_SEM_ESTOQUE`
- [ ] 5.4 Verificar que nenhum dos três serviços expõe ou chama endpoint REST de outro serviço em nenhum ponto do fluxo
- [ ] 5.5 Forçar falha de consumidor e verificar que a mensagem circula pela fila `-delayed` e, após esgotar tentativas, cai na fila `-failed`
