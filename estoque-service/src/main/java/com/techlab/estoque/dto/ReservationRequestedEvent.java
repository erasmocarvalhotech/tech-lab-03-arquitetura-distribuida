package com.techlab.estoque.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record ReservationRequestedEvent(
        String eventId,
        OffsetDateTime occurredAt,
        Long pedidoId,
        List<ItemReserva> itens
) {

    public record ItemReserva(Long produtoId, Integer quantidade) {
    }
}
