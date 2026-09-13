## ADDED Requirements

### Requirement: Cadastro e consulta de produtos
O `produto-service` SHALL prover uma API REST para criar, atualizar, consultar e inativar produtos, persistidos em banco PostgreSQL próprio (`db_produto`). Cada produto SHALL conter nome, descrição, SKU e preço. O `produto-service` SHALL NOT realizar exclusão física (`DELETE`) de um produto em nenhuma circunstância — a remoção lógica é sempre inativação (`ativo=false`), preservando o registro para evitar referências órfãs em `estoque-service` e `pedido-service`.

#### Scenario: Criação de produto com sucesso
- **WHEN** um cliente envia uma requisição de criação com nome, SKU e preço válidos
- **THEN** o sistema persiste o produto em `db_produto` e retorna os dados do produto criado

#### Scenario: Atualização de produto existente
- **WHEN** um cliente envia uma requisição de atualização de preço para um produto existente
- **THEN** o sistema atualiza o registro em `db_produto` e retorna os dados atualizados

#### Scenario: Consulta de produto por id
- **WHEN** um cliente consulta um produto por identificador existente
- **THEN** o sistema retorna nome, descrição, SKU, preço e status ativo/inativo do produto

#### Scenario: Inativação de produto nunca exclui o registro
- **WHEN** um cliente solicita a remoção de um produto existente
- **THEN** o sistema marca o produto como inativo (`ativo=false`) em `db_produto`, sem excluir a linha

### Requirement: Publicação de eventos de domínio do produto
O `produto-service` SHALL publicar um evento no exchange `mb-techlab-product-exchange-topic-product-changed` (routing key `product-changed`) imediatamente após o commit de cada operação de criação, atualização ou inativação de produto. O tipo específico da operação SHALL ser indicado pelo header `event-type` da mensagem.

#### Scenario: Evento product-created publicado após commit
- **WHEN** um produto é criado e a transação é commitada com sucesso
- **THEN** o sistema publica um evento no stream `product-changed` com header `event-type=product-created` contendo produtoId, sku e ativo

#### Scenario: Evento product-updated publicado após commit
- **WHEN** um produto existente é atualizado (ex.: mudança de preço) e a transação é commitada com sucesso
- **THEN** o sistema publica um evento no stream `product-changed` com header `event-type=product-updated` contendo produtoId e os dados alterados

#### Scenario: Evento product-deactivated publicado ao inativar
- **WHEN** um produto é marcado como inativo e a transação é commitada com sucesso
- **THEN** o sistema publica um evento no stream `product-changed` com header `event-type=product-deactivated` contendo produtoId

### Requirement: Produto não conhece quantidade de estoque
O `produto-service` SHALL NOT armazenar, expor ou publicar informação de quantidade em estoque — essa responsabilidade pertence exclusivamente ao `estoque-service`.

#### Scenario: Evento product-created não contém quantidade
- **WHEN** o evento com header `event-type=product-created` é publicado
- **THEN** o payload do evento não contém nenhum campo de quantidade ou saldo de estoque
