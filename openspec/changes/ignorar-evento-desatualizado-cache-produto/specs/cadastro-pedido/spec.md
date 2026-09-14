## MODIFIED Requirements

### Requirement: Cache local de produto mantido via eventos
O `pedido-service` SHALL manter uma projeção local em Redis (nome, preço, status ativo) de cada produto, atualizada exclusivamente por um listener próprio que consome o stream `product-changed` (fila `mb-techlab-order-queue-product-changed`, independente da fila usada pelo `estoque-service`). O `pedido-service` SHALL NOT realizar nenhuma chamada síncrona ao `produto-service` para obter esses dados.

O `pedido-service` SHALL descartar um evento `product-changed` cujo `occurredAt` não seja mais recente que o `occurredAt` já aplicado no cache local para aquele `produtoId`, para que uma entrega fora de ordem ou uma reentrega não sobrescreva um estado mais novo com um mais antigo.

#### Scenario: Upsert no cache ao consumir product-created ou product-updated
- **WHEN** o `pedido-service` consome um evento com header `event-type=product-created` ou `event-type=product-updated` cujo `occurredAt` é mais recente que o estado já registrado no cache local (ou não há estado registrado ainda)
- **THEN** o sistema grava ou atualiza a entrada correspondente no cache Redis local com nome, preço, status ativo e o `occurredAt` do evento

#### Scenario: Remocao da entrada de cache ao consumir product-deactivated
- **WHEN** o `pedido-service` consome um evento com header `event-type=product-deactivated` cujo `occurredAt` é mais recente que o estado já registrado no cache local
- **THEN** o sistema remove a chave correspondente do cache Redis local — a remoção é só da projeção em cache, nunca do registro físico do produto (que permanece em `db_produto`, apenas inativo)

#### Scenario: Evento fora de ordem é descartado sem alterar o cache
- **WHEN** o `pedido-service` consome um evento `product-changed` reconhecido (`product-created`, `product-updated` ou `product-deactivated`) cujo `occurredAt` não é mais recente que o `occurredAt` já registrado no cache local para aquele `produtoId`
- **THEN** o sistema descarta o evento sem gravar, atualizar ou remover a entrada do cache
