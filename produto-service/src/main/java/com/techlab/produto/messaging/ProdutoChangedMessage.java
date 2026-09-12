package com.techlab.produto.messaging;

import com.techlab.produto.entity.Produto;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ProdutoChangedMessage(
        String eventId,
        OffsetDateTime occurredAt,
        Long produtoId,
        String sku,
        String nome,
        BigDecimal preco,
        boolean ativo) {

    public static ProdutoChangedMessage from(Produto produto) {
        return new ProdutoChangedMessage(
                UUID.randomUUID().toString(),
                OffsetDateTime.now(),
                produto.getId(),
                produto.getSku(),
                produto.getNome(),
                produto.getPreco(),
                produto.isAtivo());
    }
}
