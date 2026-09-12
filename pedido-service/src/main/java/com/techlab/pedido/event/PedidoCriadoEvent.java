package com.techlab.pedido.event;

import com.techlab.pedido.entity.Pedido;

/**
 * Evento de aplicacao (nao AMQP) publicado apos o commit da criacao do pedido, para disparar
 * ReservationRequestedPublisher fora da transacao (mesmo padrao de ProdutoChangedEvent no
 * produto-service).
 */
public record PedidoCriadoEvent(Pedido pedido) {
}
