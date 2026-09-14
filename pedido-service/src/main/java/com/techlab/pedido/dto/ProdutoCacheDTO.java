package com.techlab.pedido.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Projecao local de produto mantida no Redis (Decisao 5 do design). Guarda o occurredAt do
 * ultimo evento aplicado para permitir descartar reentregas fora de ordem (mitigacao do risco
 * "evento fora de ordem" da spec arquitetura-microservicos).
 */
public record ProdutoCacheDTO(
        Long produtoId,
        String nome,
        BigDecimal preco,
        boolean ativo,
        OffsetDateTime occurredAt
) implements Serializable {
}
