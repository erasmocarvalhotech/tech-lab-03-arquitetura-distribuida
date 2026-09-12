package com.techlab.pedido.messaging;

import com.techlab.pedido.dto.ReservationProcessedEvent;
import com.techlab.pedido.service.PedidoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Consome mb-techlab-order-queue-reservation-processed e atualiza o status do pedido
 * correspondente. Idempotente via Pedido.confirmar()/rejeitarPorFaltaDeEstoque() - so aplica a
 * transicao se o pedido ainda estiver PENDENTE.
 */
@Component
public class ReservationProcessedListener {

    private static final Logger log = LoggerFactory.getLogger(ReservationProcessedListener.class);

    private final PedidoService pedidoService;
    private final RetryPolicy retryPolicy;
    private final String filaPrincipal;

    public ReservationProcessedListener(PedidoService pedidoService, RetryPolicy retryPolicy,
                                         @Value("${mb.techlab.order.queue.reservation.processed}") String filaPrincipal) {
        this.pedidoService = pedidoService;
        this.retryPolicy = retryPolicy;
        this.filaPrincipal = filaPrincipal;
    }

    @RabbitListener(queues = "${mb.techlab.order.queue.reservation.processed}")
    public void onReservationProcessed(ReservationProcessedEvent event, Message message) {
        try {
            aplicarResultado(event);
        } catch (Exception ex) {
            boolean esgotado = retryPolicy.tratarFalha(message, filaPrincipal, ex);
            if (!esgotado) {
                throw ex;
            }
        }
    }

    private void aplicarResultado(ReservationProcessedEvent event) {
        if (ReservationProcessedEvent.CONFIRMED.equals(event.resultado())) {
            pedidoService.confirmar(event.pedidoId());
            log.info("Pedido confirmado via reservation-processed: pedidoId={}", event.pedidoId());
        } else if (ReservationProcessedEvent.REJECTED.equals(event.resultado())) {
            pedidoService.rejeitarPorFaltaDeEstoque(event.pedidoId());
            log.info("Pedido rejeitado por falta de estoque via reservation-processed: pedidoId={}", event.pedidoId());
        } else {
            throw new IllegalArgumentException("resultado desconhecido: " + event.resultado());
        }
    }
}
