package com.techlab.estoque.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record ReservationProcessedEvent(
        String eventId,
        OffsetDateTime occurredAt,
        Long pedidoId,
        String resultado,
        List<ItemIndisponivel> itensIndisponiveis
) {

    public static final String CONFIRMED = "confirmed";
    public static final String REJECTED = "rejected";

    public record ItemIndisponivel(Long produtoId, Integer quantidadeSolicitada, Integer quantidadeDisponivel) {
    }
}
