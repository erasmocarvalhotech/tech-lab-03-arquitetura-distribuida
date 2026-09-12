package com.techlab.pedido.messaging;

import com.techlab.pedido.dto.ReservationProcessedEvent;
import com.techlab.pedido.service.PedidoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationProcessedListenerTest {

    private static final String FILA = "mb-techlab-order-queue-reservation-processed";

    @Mock
    private PedidoService pedidoService;

    @Mock
    private RetryPolicy retryPolicy;

    private ReservationProcessedListener listener;

    private final Message message = new Message(new byte[0], new MessageProperties());

    @Test
    void resultadoConfirmed_confirmaPedido() {
        listener = new ReservationProcessedListener(pedidoService, retryPolicy, FILA);
        ReservationProcessedEvent event = new ReservationProcessedEvent(
                "e1", OffsetDateTime.now(), 456L, ReservationProcessedEvent.CONFIRMED, List.of());

        listener.onReservationProcessed(event, message);

        verify(pedidoService).confirmar(456L);
        verify(pedidoService, never()).rejeitarPorFaltaDeEstoque(any());
    }

    @Test
    void resultadoRejected_rejeitaPedido() {
        listener = new ReservationProcessedListener(pedidoService, retryPolicy, FILA);
        ReservationProcessedEvent event = new ReservationProcessedEvent(
                "e1", OffsetDateTime.now(), 456L, ReservationProcessedEvent.REJECTED, List.of());

        listener.onReservationProcessed(event, message);

        verify(pedidoService).rejeitarPorFaltaDeEstoque(456L);
        verify(pedidoService, never()).confirmar(any());
    }

    @Test
    void falhaAoAplicarResultado_comCiclosDisponiveis_relancaExcecao() {
        listener = new ReservationProcessedListener(pedidoService, retryPolicy, FILA);
        ReservationProcessedEvent event = new ReservationProcessedEvent(
                "e1", OffsetDateTime.now(), 456L, ReservationProcessedEvent.CONFIRMED, List.of());
        RuntimeException falha = new RuntimeException("banco indisponivel");
        doThrow(falha).when(pedidoService).confirmar(456L);
        when(retryPolicy.tratarFalha(message, FILA, falha)).thenReturn(false);

        assertThatThrownBy(() -> listener.onReservationProcessed(event, message))
                .isSameAs(falha);
    }

    @Test
    void falhaAoAplicarResultado_comCiclosEsgotados_naoRelancaExcecao() {
        listener = new ReservationProcessedListener(pedidoService, retryPolicy, FILA);
        ReservationProcessedEvent event = new ReservationProcessedEvent(
                "e1", OffsetDateTime.now(), 456L, ReservationProcessedEvent.CONFIRMED, List.of());
        RuntimeException falha = new RuntimeException("banco indisponivel");
        doThrow(falha).when(pedidoService).confirmar(456L);
        when(retryPolicy.tratarFalha(message, FILA, falha)).thenReturn(true);

        listener.onReservationProcessed(event, message);
    }
}
