package com.techlab.pedido.dto;

import com.techlab.pedido.entity.Pedido;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ReservationRequestedEvent(
        String eventId,
        OffsetDateTime occurredAt,
        Long pedidoId,
        List<ItemReserva> itens
) {

    public record ItemReserva(Long produtoId, Integer quantidade) {
    }

    public static ReservationRequestedEvent from(Pedido pedido) {
        List<ItemReserva> itens = pedido.getItens().stream()
                .map(item -> new ItemReserva(item.getProdutoId(), item.getQuantidade()))
                .toList();
        return new ReservationRequestedEvent(UUID.randomUUID().toString(), OffsetDateTime.now(), pedido.getId(), itens);
    }
}
