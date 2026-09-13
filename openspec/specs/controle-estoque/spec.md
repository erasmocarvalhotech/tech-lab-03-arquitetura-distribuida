# controle-estoque Specification

## Purpose
TBD - created by archiving change arquitetura-microservicos. Update Purpose after archive.
## Requirements
### Requirement: Sincronizacao de saldo a partir de eventos de produto
O `estoque-service` SHALL consumir o stream `product-changed` (fila `mb-techlab-stock-queue-product-changed`) e manter um registro de saldo (`SaldoEstoque`) por produto em banco PostgreSQL próprio (`db_estoque`). O `estoque-service` é o único dono da informação de quantidade disponível e reservada.

#### Scenario: Criacao de saldo zerado ao consumir product-created
- **WHEN** o `estoque-service` consome um evento com header `event-type=product-created`
- **THEN** o sistema cria um registro de `SaldoEstoque` para o produtoId com quantidade disponível igual a zero

#### Scenario: Carga inicial de estoque é ação separada
- **WHEN** um operador registra uma entrada de estoque para um produto já existente
- **THEN** o sistema incrementa a quantidade disponível daquele produto, sem depender de nenhum dado vindo do `produto-service`

### Requirement: Reserva de estoque via lock otimista
O `estoque-service` SHALL processar solicitações de reserva de estoque (consumidas da fila `mb-techlab-stock-queue-reservation-requested`) utilizando lock otimista (`@Version`) sobre o registro de `SaldoEstoque`, distinguindo conflito de concorrência de estoque insuficiente por regra de negócio.

#### Scenario: Reserva bem-sucedida
- **WHEN** o `estoque-service` consome um evento `reservation-requested` para um produto com quantidade disponível suficiente e sem conflito de escrita concorrente
- **THEN** o sistema decrementa a quantidade disponível, incrementa a quantidade reservada, e considera a reserva bem-sucedida

#### Scenario: Reserva rejeitada por estoque insuficiente
- **WHEN** o `estoque-service` consome um evento `reservation-requested` para um produto sem quantidade disponível suficiente
- **THEN** o sistema não altera o saldo, considera a reserva como indisponível e não aciona nenhum mecanismo de retry — é tratado como resultado de negócio, não como erro

#### Scenario: Conflito de concorrência aciona retry via fila delayed
- **WHEN** o `estoque-service` tenta salvar a reserva e ocorre `OptimisticLockException` (outra transação alterou o saldo entre a leitura e o commit)
- **THEN** a mensagem não é confirmada ao broker e é reprocessada automaticamente através da fila `-delayed` correspondente, sem publicar nenhum resultado de negócio nessa tentativa

### Requirement: Publicacao da resposta da reserva de estoque
O `estoque-service` SHALL publicar no exchange `mb-techlab-stock-exchange-topic-reservation-processed` (routing key `reservation-processed`) o resultado de cada tentativa de reserva, imediatamente após o commit da transação correspondente, indicando o resultado via header/campo `confirmed` ou `rejected`.

#### Scenario: Publica resultado confirmado
- **WHEN** uma reserva de estoque é bem-sucedida e a transação é commitada
- **THEN** o sistema publica um evento `reservation-processed` com resultado `confirmed`, contendo o pedidoId e os itens reservados

#### Scenario: Publica resultado rejeitado
- **WHEN** uma reserva de estoque falha por quantidade insuficiente
- **THEN** o sistema publica um evento `reservation-processed` com resultado `rejected`, contendo o pedidoId e os itens que não puderam ser reservados

