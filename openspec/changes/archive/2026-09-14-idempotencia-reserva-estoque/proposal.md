## Why

Uma auditoria independente identificou uma lacuna real em `estoque-service`: `SaldoEstoqueService.reservar()` não verifica se o `pedidoId` daquela solicitação já foi processado antes de decrementar o saldo. Se o broker reentregar a mensagem de `reservation-requested` por um motivo que não seja o ciclo controlado `-delayed` (ex.: o consumidor commitou a reserva no banco mas caiu antes de confirmar o ACK ao RabbitMQ — reentrega crua, fora do mecanismo de `x-death`/retry já desenhado), o mesmo pedido reserva estoque duas vezes.

O restante do sistema já trata idempotência com cuidado explícito nesse mesmo padrão (`criarSaldoZerado` verifica `existsByProdutoId`; `Pedido.confirmar()`/`rejeitarPorFaltaDeEstoque()` só transicionam a partir de `PENDENTE`) — essa é a única lacuna, e está justamente na operação mais sensível (alteração de saldo).

## What Changes

- `estoque-service` passa a registrar cada reserva processada por `pedidoId` (nova tabela `tb_reserva_processada`, com `pedido_id` único).
- `SaldoEstoqueService.reservar` passa a receber `pedidoId` como parâmetro e, antes de decrementar qualquer saldo, verifica se aquele `pedidoId` já foi processado — se sim, retorna o resultado já registrado (replay idempotente), sem tocar no saldo de novo.
- O registro da reserva processada é gravado na **mesma transação** da alteração de saldo (ou da decisão de rejeitar por estoque insuficiente), garantindo que os dois efeitos (saldo + registro de idempotência) sejam atômicos.

## Capabilities

### Modified Capabilities
- `controle-estoque`: adiciona garantia de idempotência por `pedidoId` na reserva de estoque, complementando (não substituindo) o requisito existente "Reserva de estoque via lock otimista".

## Impact

- Nova tabela em `db_estoque` (`tb_reserva_processada`), nova migration Flyway.
- Mudança de assinatura em `SaldoEstoqueService.reservar` (novo parâmetro `pedidoId`) e no ponto de chamada em `ReservationRequestedListener`.
- Sem mudança de contrato de mensageria (payload de `reservation-requested`/`reservation-processed` já contém `pedidoId`) nem de API REST.
