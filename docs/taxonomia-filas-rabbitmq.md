# Taxonomia de filas e exchanges (RabbitMQ)

Convenção de nomenclatura adotada neste laboratório para recursos RabbitMQ, inspirada em padrão corporativo de nomenclatura de message broker (não reproduzido aqui — apenas a convenção de nomes é aplicada).

## Regras gerais

- Prefixo obrigatório: `mb` (Message Broker)
- Kebab-case, em inglês
- Sem vhost dedicado neste lab (single vhost padrão) e sem sufixo de ambiente no nome do recurso
- Diferenciação entre tipos de evento de um mesmo stream (ex.: criado/atualizado/inativado) via **header da mensagem**, não via routing key/exchange separada — decisão deste lab para reduzir a quantidade de recursos
- Resiliência em 3 camadas por fila principal: fila principal → `-delayed` (retry com backoff) → `-failed` (DLQ definitiva)

## Padrões

| Recurso | Padrão |
|---|---|
| Exchange | `mb-{domain}-exchange-{tipo}-{stream}` |
| Fila | `mb-{domain}-queue-{stream}` |
| Routing key | `{stream}` |
| Fila delayed | `mb-{domain}-queue-{stream}-delayed` (TTL + DLX default exchange `""` + routing key = nome da fila principal) |
| Exchange delayed | `mb-{domain}-exchange-fanout-{stream}-delayed` |
| Fila failed | `mb-{domain}-queue-{stream}-failed` (sem TTL) |
| Exchange failed | `mb-{domain}-exchange-fanout-{stream}-failed` |

Header de discriminação de evento: `event-type` com valores no particípio passado (`product-created`, `product-updated`, `product-deactivated`).

## Recursos deste laboratório

Domínios: `techlab-product`, `techlab-order`, `techlab-stock`.

### Stream: mudanças de produto (Produto → Estoque e Pedido)

- Exchange: `mb-techlab-product-exchange-topic-product-changed`
- Routing key: `product-changed`
- Header `event-type`: `product-created` | `product-updated` | `product-deactivated` (nunca há evento de exclusão física — produto nunca é `DELETE`d, só inativado)
- Fila (consumo Estoque): `mb-techlab-stock-queue-product-changed` (+ `-delayed` + `-failed`)
- Fila (consumo Pedido): `mb-techlab-order-queue-product-changed` (+ `-delayed` + `-failed`)

### Stream: solicitação de reserva (Pedido → Estoque)

- Exchange: `mb-techlab-order-exchange-topic-reservation-requested`
- Routing key: `reservation-requested`
- Fila (consumo Estoque): `mb-techlab-stock-queue-reservation-requested` (+ `-delayed` + `-failed`)

### Stream: resultado da reserva (Estoque → Pedido)

- Exchange: `mb-techlab-stock-exchange-topic-reservation-processed`
- Routing key: `reservation-processed`
- Header `event-type` (ou campo `status` no payload): `confirmed` | `rejected`
- Fila (consumo Pedido): `mb-techlab-order-queue-reservation-processed` (+ `-delayed` + `-failed`)

## Propriedades Spring (espelho pacote ↔ broker)

```properties
mb.techlab.product.exchange.topic.product.changed=mb-techlab-product-exchange-topic-product-changed
mb.techlab.product.routing.key.product.changed=product-changed

mb.techlab.stock.queue.product.changed=mb-techlab-stock-queue-product-changed
mb.techlab.stock.queue.product.changed.delayed=mb-techlab-stock-queue-product-changed-delayed
mb.techlab.stock.queue.product.changed.failed=mb-techlab-stock-queue-product-changed-failed

mb.techlab.order.queue.product.changed=mb-techlab-order-queue-product-changed
mb.techlab.order.queue.product.changed.delayed=mb-techlab-order-queue-product-changed-delayed
mb.techlab.order.queue.product.changed.failed=mb-techlab-order-queue-product-changed-failed

mb.techlab.order.exchange.topic.reservation.requested=mb-techlab-order-exchange-topic-reservation-requested
mb.techlab.order.routing.key.reservation.requested=reservation-requested
mb.techlab.stock.queue.reservation.requested=mb-techlab-stock-queue-reservation-requested
mb.techlab.stock.queue.reservation.requested.delayed=mb-techlab-stock-queue-reservation-requested-delayed
mb.techlab.stock.queue.reservation.requested.failed=mb-techlab-stock-queue-reservation-requested-failed

mb.techlab.stock.exchange.topic.reservation.processed=mb-techlab-stock-exchange-topic-reservation-processed
mb.techlab.stock.routing.key.reservation.processed=reservation-processed
mb.techlab.order.queue.reservation.processed=mb-techlab-order-queue-reservation-processed
mb.techlab.order.queue.reservation.processed.delayed=mb-techlab-order-queue-reservation-processed-delayed
mb.techlab.order.queue.reservation.processed.failed=mb-techlab-order-queue-reservation-processed-failed
```
