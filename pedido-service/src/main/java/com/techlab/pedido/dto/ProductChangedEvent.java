package com.techlab.pedido.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ProductChangedEvent(
        String eventId,
        OffsetDateTime occurredAt,
        Long produtoId,
        String sku,
        String nome,
        BigDecimal preco,
        boolean ativo
) {
}
