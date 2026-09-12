package com.techlab.pedido.messaging;

import com.techlab.pedido.dto.ReservationRequestedEvent;
import com.techlab.pedido.event.PedidoCriadoEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Publica reservation-requested somente apos o commit da transacao que criou o pedido (mesmo
 * padrao do ProdutoEventPublisher no produto-service).
 */
@Component
public class ReservationRequestedPublisher {

    private static final Logger log = LoggerFactory.getLogger(ReservationRequestedPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final String exchange;
    private final String routingKey;

    public ReservationRequestedPublisher(
            RabbitTemplate rabbitTemplate,
            @Value("${mb.techlab.order.exchange.topic.reservation.requested}") String exchange,
            @Value("${mb.techlab.order.routing.key.reservation.requested}") String routingKey) {
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
        this.routingKey = routingKey;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPedidoCriado(PedidoCriadoEvent event) {
        ReservationRequestedEvent payload = ReservationRequestedEvent.from(event.pedido());
        rabbitTemplate.convertAndSend(exchange, routingKey, payload);
        log.info("Evento reservation-requested publicado para pedidoId={}", payload.pedidoId());
    }
}
