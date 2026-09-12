package com.techlab.pedido.messaging;

import com.techlab.pedido.dto.ReservationRequestedEvent;
import com.techlab.pedido.entity.ItemPedido;
import com.techlab.pedido.entity.Pedido;
import com.techlab.pedido.event.PedidoCriadoEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ReservationRequestedPublisherTest {

    private static final String EXCHANGE = "mb-techlab-order-exchange-topic-reservation-requested";
    private static final String ROUTING_KEY = "reservation-requested";

    @Test
    void onPedidoCriado_publicaItensDoPedidoNoExchangeDeReserva() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        ReservationRequestedPublisher publisher = new ReservationRequestedPublisher(rabbitTemplate, EXCHANGE, ROUTING_KEY);

        Pedido pedido = new Pedido(List.of(new ItemPedido(123L, 2, new BigDecimal("19.90"))));
        ReflectionTestUtils.setField(pedido, "id", 456L);

        publisher.onPedidoCriado(new PedidoCriadoEvent(pedido));

        ArgumentCaptor<ReservationRequestedEvent> captor = ArgumentCaptor.forClass(ReservationRequestedEvent.class);
        verify(rabbitTemplate).convertAndSend(eq(EXCHANGE), eq(ROUTING_KEY), captor.capture());
        assertThat(captor.getValue().pedidoId()).isEqualTo(456L);
        assertThat(captor.getValue().itens()).containsExactly(
                new ReservationRequestedEvent.ItemReserva(123L, 2));
    }
}
