## 1. Modelo de dados

- [x] 1.1 Criar migration `V2__create_tb_reserva_processada.sql` em `estoque-service` (`pedido_id BIGINT NOT NULL UNIQUE`, `resultado VARCHAR(20) NOT NULL`, `itens_indisponiveis` em formato JSON/texto, `processado_em TIMESTAMPTZ DEFAULT now()`)
- [x] 1.2 Entity `ReservaProcessada` (`@Table(name = "tb_reserva_processada")`) + `ReservaProcessadaRepository` com `findByPedidoId`

## 2. Lógica de idempotência

- [x] 2.1 Alterar assinatura de `SaldoEstoqueService.reservar` para receber `pedidoId` além dos itens
- [x] 2.2 No início do método, consultar `ReservaProcessadaRepository.findByPedidoId`; se existir, retornar o resultado já registrado (idempotente) sem tocar no saldo
- [x] 2.3 Ao final do processamento (confirmado ou rejeitado), persistir o registro em `tb_reserva_processada` na mesma transação
- [x] 2.4 Atualizar `ReservationRequestedListener` para passar `event.pedidoId()` na chamada a `reservar`

## 3. Testes

- [x] 3.1 Teste: primeira reserva de um `pedidoId` decrementa o saldo e grava o registro de idempotência
- [x] 3.2 Teste: reentrega do mesmo `pedidoId` (chamando `reservar` de novo com os mesmos parâmetros) não altera o saldo e retorna o mesmo resultado
- [x] 3.3 Teste: reentrega após reserva rejeitada (estoque insuficiente) retorna o mesmo resultado de rejeição, sem nova tentativa de decremento
- [x] 3.4 Rodar `mvn clean test` em `estoque-service` e confirmar que os testes existentes (`SaldoEstoqueServiceTest`) continuam passando com a nova assinatura

## 4. Validação end-to-end

- [x] 4.1 Subir os 3 serviços + infraestrutura via docker-compose
- [x] 4.2 Criar produto, entrada de estoque, criar pedido — confirmar fluxo feliz inalterado (`CONFIRMADO`)
- [x] 4.3 Simular reentrega manual: publicar a mesma mensagem `reservation-requested` (mesmo `pedidoId`) duas vezes direto no RabbitMQ Management e confirmar que o saldo só é decrementado uma vez
