package com.techlab.estoque.messaging;

import com.techlab.estoque.dto.ReservationProcessedEvent;
import com.techlab.estoque.dto.ReservationRequestedEvent;
import com.techlab.estoque.service.ResultadoReserva;
import com.techlab.estoque.service.SaldoEstoqueService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Component
public class ReservationRequestedListener {

    private static final Logger log = LoggerFactory.getLogger(ReservationRequestedListener.class);

    private final SaldoEstoqueService saldoEstoqueService;
    private final EstoqueEventPublisher publisher;
    private final RetryPolicy retryPolicy;
    private final String filaPrincipal;

    public ReservationRequestedListener(SaldoEstoqueService saldoEstoqueService, EstoqueEventPublisher publisher,
                                         RetryPolicy retryPolicy,
                                         @Value("${mb.techlab.stock.queue.reservation.requested}") String filaPrincipal) {
        this.saldoEstoqueService = saldoEstoqueService;
        this.publisher = publisher;
        this.retryPolicy = retryPolicy;
        this.filaPrincipal = filaPrincipal;
    }

    @RabbitListener(queues = "${mb.techlab.stock.queue.reservation.requested}")
    public void onReservationRequested(ReservationRequestedEvent event, Message message) {
        try {
            ResultadoReserva resultado = saldoEstoqueService.reservar(event.pedidoId(), event.itens());
            publisher.publicarReservationProcessed(paraEvento(event, resultado));
            log.info("Reserva de estoque processada: pedidoId={} confirmada={}", event.pedidoId(), resultado.confirmada());
        } catch (Exception ex) {
            // OptimisticLockingFailureException (conflito de concorrencia) e qualquer outra falha
            // tecnica caem aqui - nao publicam resultado de negocio nesta tentativa (Decisao 3).
            boolean esgotado = retryPolicy.tratarFalha(message, filaPrincipal, ex);
            if (!esgotado) {
                throw ex;
            }
        }
    }

    private ReservationProcessedEvent paraEvento(ReservationRequestedEvent event, ResultadoReserva resultado) {
        String resultadoTexto = resultado.confirmada() ? ReservationProcessedEvent.CONFIRMED : ReservationProcessedEvent.REJECTED;
        List<ReservationProcessedEvent.ItemIndisponivel> itensIndisponiveis = resultado.confirmada()
                ? List.of()
                : resultado.itensIndisponiveis();
        return new ReservationProcessedEvent(
                UUID.randomUUID().toString(),
                OffsetDateTime.now(),
                event.pedidoId(),
                resultadoTexto,
                itensIndisponiveis
        );
    }
}
