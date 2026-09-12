## ADDED Requirements

### Requirement: Cache local de produto mantido via eventos
O `pedido-service` SHALL manter uma projeção local em Redis (nome, preço, status ativo) de cada produto, atualizada exclusivamente por um listener próprio que consome o stream `product-changed` (fila `mb-techlab-order-queue-product-changed`, independente da fila usada pelo `estoque-service`). O `pedido-service` SHALL NOT realizar nenhuma chamada síncrona ao `produto-service` para obter esses dados.

#### Scenario: Upsert no cache ao consumir product-created ou product-updated
- **WHEN** o `pedido-service` consome um evento com header `event-type=product-created` ou `event-type=product-updated`
- **THEN** o sistema grava ou atualiza a entrada correspondente no cache Redis local com nome, preço e status ativo

#### Scenario: Remocao da entrada de cache ao consumir product-deactivated
- **WHEN** o `pedido-service` consome um evento com header `event-type=product-deactivated`
- **THEN** o sistema remove a chave correspondente do cache Redis local — a remoção é só da projeção em cache, nunca do registro físico do produto (que permanece em `db_produto`, apenas inativo)

### Requirement: Criacao de pedido com snapshot de preco
O `pedido-service` SHALL, ao criar um pedido, ler o preço de cada item exclusivamente do cache local de produto e gravar esse valor como snapshot imutável no item do pedido (persistido em `db_pedido`), independente de alterações futuras de preço no catálogo.

#### Scenario: Criacao de pedido usando preco do cache local
- **WHEN** um cliente cria um pedido para um produto presente no cache local e ativo
- **THEN** o sistema cria o pedido com status `PENDENTE`, gravando o preço atual do cache como `precoUnitario` do item

#### Scenario: Rejeicao por produto nao encontrado no cache local
- **WHEN** um cliente tenta criar um pedido para um produtoId ausente ou inativo no cache local
- **THEN** o sistema rejeita a criação do pedido sem consultar o `produto-service` de forma síncrona

### Requirement: Solicitacao de reserva de estoque
Ao criar um pedido com status `PENDENTE`, o `pedido-service` SHALL publicar um evento no exchange `mb-techlab-order-exchange-topic-reservation-requested` (routing key `reservation-requested`), imediatamente após o commit da transação, solicitando a reserva de estoque dos itens do pedido.

#### Scenario: Publica reservation-requested ao criar pedido
- **WHEN** um pedido é criado com sucesso e a transação é commitada
- **THEN** o sistema publica um evento `reservation-requested` contendo o pedidoId e a lista de itens (produtoId, quantidade)

### Requirement: Confirmacao ou rejeicao do pedido via eventos de estoque
O `pedido-service` SHALL consumir a fila `mb-techlab-order-queue-reservation-processed` e atualizar o status do pedido correspondente com base no resultado da reserva de estoque.

#### Scenario: Confirma pedido ao consumir resultado confirmed
- **WHEN** o `pedido-service` consome um evento `reservation-processed` com resultado `confirmed` para um pedido em status `PENDENTE`
- **THEN** o sistema atualiza o status do pedido para `CONFIRMADO`

#### Scenario: Rejeita pedido ao consumir resultado rejected
- **WHEN** o `pedido-service` consome um evento `reservation-processed` com resultado `rejected` para um pedido em status `PENDENTE`
- **THEN** o sistema atualiza o status do pedido para `REJEITADO_SEM_ESTOQUE`
