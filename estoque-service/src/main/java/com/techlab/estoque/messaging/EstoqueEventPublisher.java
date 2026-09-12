package com.techlab.estoque.messaging;

import com.techlab.estoque.dto.ReservationProcessedEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class EstoqueEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final String exchangeReservationProcessed;
    private final String routingKeyReservationProcessed;

    public EstoqueEventPublisher(RabbitTemplate rabbitTemplate,
                                  @Value("${mb.techlab.stock.exchange.topic.reservation.processed}") String exchangeReservationProcessed,
                                  @Value("${mb.techlab.stock.routing.key.reservation.processed}") String routingKeyReservationProcessed) {
        this.rabbitTemplate = rabbitTemplate;
        this.exchangeReservationProcessed = exchangeReservationProcessed;
        this.routingKeyReservationProcessed = routingKeyReservationProcessed;
    }

    public void publicarReservationProcessed(ReservationProcessedEvent event) {
        rabbitTemplate.convertAndSend(exchangeReservationProcessed, routingKeyReservationProcessed, event);
    }
}
