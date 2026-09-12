package com.techlab.estoque.dto;

import java.time.OffsetDateTime;

public record ProductChangedEvent(
        String eventId,
        OffsetDateTime occurredAt,
        Long produtoId,
        String sku,
        String nome,
        java.math.BigDecimal preco,
        boolean ativo
) {
}
