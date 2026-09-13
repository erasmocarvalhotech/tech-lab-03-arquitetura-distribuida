## Context

`estoque-service` já trata idempotência em outros pontos (`criarSaldoZerado` verifica existência antes de criar; `Pedido` no `pedido-service` só transiciona de status a partir de `PENDENTE`). A reserva de estoque em si (`SaldoEstoqueService.reservar`) é a exceção: não há verificação de que aquele `pedidoId` já foi processado antes de decrementar o saldo.

O mecanismo de retry via `x-death` (Decisão 7 da spec arquiteturas-microservicos, arquivada) cobre falhas *durante* o processamento (exceção lançada, mensagem rejeitada, cai em `-delayed`). Não cobre o cenário onde a transação **já commitou** com sucesso e o consumidor falha (crash, rede) **antes** de confirmar o ACK ao broker — nesse caso o RabbitMQ reentrega a mensagem original, fora do ciclo `-delayed`, e ela chega ao listener como se fosse a primeira vez.

## Goals / Non-Goals

**Goals:**
- Garantir que a mesma solicitação de reserva (mesmo `pedidoId`) nunca decremente o saldo mais de uma vez, independente de quantas vezes a mensagem for reentregue.
- Garantir que o resultado publicado em `reservation-processed` seja o mesmo em toda reentrega do mesmo `pedidoId` (replay idempotente do resultado, não reprocessamento da regra de negócio).

**Non-Goals:**
- Não é objetivo implementar outbox transacional (publicação após commit continua via `@TransactionalEventListener(AFTER_COMMIT)`, sem mudança nesta frente — risco de dual-write permanece aceito e será só documentado, não resolvido aqui).
- Não é objetivo adicionar teste de integração com Testcontainers nesta mudança — fica registrado como gap conhecido, possível mudança futura.

## Decisions

### 1. Tabela `tb_reserva_processada` com `pedido_id` único, checada no início de `reservar`

`SaldoEstoqueService.reservar` passa a receber `pedidoId` e, antes de qualquer leitura/alteração de saldo, consulta se já existe um registro para aquele `pedidoId`. Se existir, retorna o resultado já gravado (idempotente) sem tocar no saldo. Se não existir, executa a lógica atual (valida disponibilidade, decrementa se possível) e, **na mesma transação**, grava o registro de `tb_reserva_processada` com o resultado.

**Por quê**: mesmo padrão já usado em `criarSaldoZerado` (verificar existência antes de agir) — consistente com o resto do código, sem introduzir um mecanismo novo. Gravar o registro na mesma transação da alteração de saldo garante atomicidade: ou as duas coisas commitam juntas, ou nenhuma.

**Alternativa considerada**: usar só o `@Version` existente como proteção (assumindo que uma segunda tentativa causaria conflito otimista). Rejeitada — o `@Version` protege contra *concorrência* (duas transações ao mesmo tempo), não contra *reentrega sequencial* da mesma mensagem já commitada (não há conflito de escrita nesse caso, a segunda execução simplesmente decrementa de novo, com sucesso, sobre o novo valor).

### 2. Corrida no INSERT do registro de idempotência é tratada como falha técnica (mesmo caminho do `x-death` já existente)

Se duas reentregas do mesmo `pedidoId` chegarem a competir (ex.: duas threads de consumidor), a constraint `UNIQUE(pedido_id)` rejeita o segundo INSERT com `DataIntegrityViolationException`. Essa exceção **não é tratada especialmente** — propaga como qualquer outra falha técnica, cai no mesmo fluxo de retry via `-delayed` já implementado (`RetryPolicy`/`XDeathInspector`). Na próxima tentativa, o SELECT inicial já encontra o registro gravado pela transação concorrente vencedora, e o caminho idempotente (retornar resultado já registrado) é tomado.

**Por quê**: reaproveita a infraestrutura de retry que já existe, em vez de criar tratamento de exceção dedicado só para esse caso — consistente com a filosofia de simplicidade já adotada no projeto.

## Risks / Trade-offs

| Risco | Mitigação / decisão |
|---|---|
| Tabela `tb_reserva_processada` cresce indefinidamente (nunca há limpeza) | Aceitável para o lab; em produção valeria um job de arquivamento/expurgo por idade — fora do escopo desta mudança. |
| Dual-write entre commit da reserva e publicação do evento (`rabbitTemplate.convertAndSend` pode falhar após commit bem-sucedido) | Risco já conhecido e aceito desde a spec original (arquivada); não resolvido por esta mudança, permanece como debt documentado, não implementado (outbox transacional ficaria como evolução futura). |
| Nenhum teste de integração (Testcontainers) prova o cenário real de reentrega crua reproduzindo o bug antes do fix | Aceito para esta mudança; teste unitário cobre a lógica de decisão (idempotente vs. primeira vez), mas não o comportamento real do broker. Fica registrado como gap conhecido (mesma lacuna já levantada na auditoria para o restante do sistema). |

## Migration Plan

Aditivo — nova tabela, nova migration Flyway (`V2__create_tb_reserva_processada.sql`), sem alteração de schema existente. Mudança de assinatura em `SaldoEstoqueService.reservar` é interna ao serviço (chamada só por `ReservationRequestedListener`), sem impacto em contrato externo.
