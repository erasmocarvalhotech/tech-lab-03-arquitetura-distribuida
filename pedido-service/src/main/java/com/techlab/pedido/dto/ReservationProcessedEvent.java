package com.techlab.pedido.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Payload consumido pelo pedido-service no stream reservation-processed. O resultado
 * ("confirmed" | "rejected") vai no payload, nao em header - ver Decisao 6 do design.md
 * (diferente de product-changed).
 */
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
