package com.techlab.pedido.messaging;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class XDeathInspectorTest {

    private final XDeathInspector inspector = new XDeathInspector();

    @Test
    void semHeaderXDeath_retornaZero() {
        Message message = new Message(new byte[0], new MessageProperties());

        long ciclos = inspector.countCiclosDelayed(message, "mb-techlab-order-queue-product-changed-delayed");

        assertThat(ciclos).isZero();
    }

    @Test
    void comEntradaExpiredNaFilaDelayed_retornaCount() {
        MessageProperties properties = new MessageProperties();
        properties.setHeader("x-death", List.of(
                Map.of("queue", "mb-techlab-order-queue-product-changed-delayed", "reason", "expired", "count", 2L)
        ));
        Message message = new Message(new byte[0], properties);

        long ciclos = inspector.countCiclosDelayed(message, "mb-techlab-order-queue-product-changed-delayed");

        assertThat(ciclos).isEqualTo(2L);
    }

    @Test
    void comEntradaDeOutraFila_ignoraERetornaZero() {
        MessageProperties properties = new MessageProperties();
        properties.setHeader("x-death", List.of(
                Map.of("queue", "outra-fila-delayed", "reason", "expired", "count", 5L)
        ));
        Message message = new Message(new byte[0], properties);

        long ciclos = inspector.countCiclosDelayed(message, "mb-techlab-order-queue-product-changed-delayed");

        assertThat(ciclos).isZero();
    }

    @Test
    void comEntradaRejectedEmVezDeExpired_ignoraERetornaZero() {
        MessageProperties properties = new MessageProperties();
        properties.setHeader("x-death", List.of(
                Map.of("queue", "mb-techlab-order-queue-product-changed-delayed", "reason", "rejected", "count", 3L)
        ));
        Message message = new Message(new byte[0], properties);

        long ciclos = inspector.countCiclosDelayed(message, "mb-techlab-order-queue-product-changed-delayed");

        assertThat(ciclos).isZero();
    }
}
