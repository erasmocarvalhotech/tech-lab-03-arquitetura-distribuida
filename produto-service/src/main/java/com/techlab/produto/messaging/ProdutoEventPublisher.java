package com.techlab.produto.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ProdutoEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(ProdutoEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final String exchange;
    private final String routingKey;

    public ProdutoEventPublisher(
            RabbitTemplate rabbitTemplate,
            @Value("${mb.techlab.product.exchange.topic.product-changed}") String exchange,
            @Value("${mb.techlab.product.routing-key.product-changed}") String routingKey) {
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
        this.routingKey = routingKey;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProdutoChanged(ProdutoChangedEvent event) {
        ProdutoChangedMessage payload = ProdutoChangedMessage.from(event.produto());
        MessagePostProcessor addEventTypeHeader =
                message -> {
                    message.getMessageProperties().setHeader("event-type", event.tipo().eventType());
                    return message;
                };
        rabbitTemplate.convertAndSend(exchange, routingKey, payload, addEventTypeHeader);
        log.info("Evento {} publicado para produtoId={}", event.tipo().eventType(), payload.produtoId());
    }
}
