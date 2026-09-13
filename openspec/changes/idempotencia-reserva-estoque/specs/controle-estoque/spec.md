## ADDED Requirements

### Requirement: Idempotência da reserva de estoque por pedidoId
O `estoque-service` SHALL garantir que uma solicitação de reserva (identificada por `pedidoId`) nunca altere o saldo de estoque mais de uma vez, mesmo que a mensagem `reservation-requested` correspondente seja reentregue pelo broker fora do ciclo de retry `-delayed`.

#### Scenario: Reentrega da mesma solicitação não decrementa o saldo de novo
- **WHEN** o `estoque-service` já processou com sucesso uma reserva para um `pedidoId` e a mesma mensagem `reservation-requested` é reentregue pelo broker
- **THEN** o sistema não altera o saldo novamente e retorna o mesmo resultado já registrado para aquele `pedidoId`

#### Scenario: Registro de idempotência é gravado atomicamente com a alteração de saldo
- **WHEN** uma reserva é processada pela primeira vez para um `pedidoId` (seja confirmada ou rejeitada por estoque insuficiente)
- **THEN** o registro de idempotência daquele `pedidoId` é gravado na mesma transação da decisão de reserva — ambos commitam juntos ou nenhum dos dois é persistido
