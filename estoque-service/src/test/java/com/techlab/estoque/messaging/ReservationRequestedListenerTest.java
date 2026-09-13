package com.techlab.estoque.messaging;

import com.techlab.estoque.dto.ReservationProcessedEvent;
import com.techlab.estoque.dto.ReservationProcessedEvent.ItemIndisponivel;
import com.techlab.estoque.dto.ReservationRequestedEvent;
import com.techlab.estoque.dto.ReservationRequestedEvent.ItemReserva;
import com.techlab.estoque.service.ResultadoReserva;
import com.techlab.estoque.service.SaldoEstoqueService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.dao.OptimisticLockingFailureException;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationRequestedListenerTest {

    private static final String FILA = "mb-techlab-stock-queue-reservation-requested";

    @Mock
    private SaldoEstoqueService saldoEstoqueService;

    @Mock
    private EstoqueEventPublisher publisher;

    @Mock
    private RetryPolicy retryPolicy;

    private ReservationRequestedListener listener;

    private final Message message = new Message(new byte[0], new MessageProperties());

    @Test
    void reservaConfirmada_publicaResultadoConfirmed() {
        listener = new ReservationRequestedListener(saldoEstoqueService, publisher, retryPolicy, FILA);
        ReservationRequestedEvent event = new ReservationRequestedEvent("e1", OffsetDateTime.now(), 456L,
                List.of(new ItemReserva(123L, 2)));
        when(saldoEstoqueService.reservar(event.pedidoId(), event.itens())).thenReturn(ResultadoReserva.reservaConfirmada());

        listener.onReservationRequested(event, message);

        ArgumentCaptor<ReservationProcessedEvent> captor = ArgumentCaptor.forClass(ReservationProcessedEvent.class);
        verify(publisher).publicarReservationProcessed(captor.capture());
        assertThat(captor.getValue().resultado()).isEqualTo(ReservationProcessedEvent.CONFIRMED);
        assertThat(captor.getValue().pedidoId()).isEqualTo(456L);
        verify(retryPolicy, never()).tratarFalha(any(), any(), any());
    }

    @Test
    void reservaRejeitadaPorEstoqueInsuficiente_publicaRejectedSemAcionarRetry() {
        listener = new ReservationRequestedListener(saldoEstoqueService, publisher, retryPolicy, FILA);
        ReservationRequestedEvent event = new ReservationRequestedEvent("e1", OffsetDateTime.now(), 456L,
                List.of(new ItemReserva(123L, 5)));
        ItemIndisponivel indisponivel = new ItemIndisponivel(123L, 5, 2);
        when(saldoEstoqueService.reservar(event.pedidoId(), event.itens())).thenReturn(ResultadoReserva.reservaRejeitada(List.of(indisponivel)));

        listener.onReservationRequested(event, message);

        ArgumentCaptor<ReservationProcessedEvent> captor = ArgumentCaptor.forClass(ReservationProcessedEvent.class);
        verify(publisher).publicarReservationProcessed(captor.capture());
        assertThat(captor.getValue().resultado()).isEqualTo(ReservationProcessedEvent.REJECTED);
        assertThat(captor.getValue().itensIndisponiveis()).containsExactly(indisponivel);
        verify(retryPolicy, never()).tratarFalha(any(), any(), any());
    }

    @Test
    void conflitoDeConcorrencia_naoPublicaNadaEAplicaRetryPolicy() {
        listener = new ReservationRequestedListener(saldoEstoqueService, publisher, retryPolicy, FILA);
        ReservationRequestedEvent event = new ReservationRequestedEvent("e1", OffsetDateTime.now(), 456L,
                List.of(new ItemReserva(123L, 2)));
        OptimisticLockingFailureException conflito = new OptimisticLockingFailureException("conflito de versao");
        when(saldoEstoqueService.reservar(event.pedidoId(), event.itens())).thenThrow(conflito);
        when(retryPolicy.tratarFalha(message, FILA, conflito)).thenReturn(false);

        assertThatThrownBy(() -> listener.onReservationRequested(event, message))
                .isSameAs(conflito);

        verify(publisher, never()).publicarReservationProcessed(any());
    }

    @Test
    void conflitoPersistenteComCiclosEsgotados_naoRelancaExcecao() {
        listener = new ReservationRequestedListener(saldoEstoqueService, publisher, retryPolicy, FILA);
        ReservationRequestedEvent event = new ReservationRequestedEvent("e1", OffsetDateTime.now(), 456L,
                List.of(new ItemReserva(123L, 2)));
        OptimisticLockingFailureException conflito = new OptimisticLockingFailureException("conflito de versao");
        when(saldoEstoqueService.reservar(event.pedidoId(), event.itens())).thenThrow(conflito);
        when(retryPolicy.tratarFalha(message, FILA, conflito)).thenReturn(true);

        listener.onReservationRequested(event, message);

        verify(publisher, never()).publicarReservationProcessed(any());
    }

    private static <T> T any() {
        return org.mockito.ArgumentMatchers.any();
    }
}
