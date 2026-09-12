package com.techlab.pedido.dto;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Projecao local de produto mantida no Redis (Decisao 5 do design).
 */
public record ProdutoCacheDTO(
        Long produtoId,
        String nome,
        BigDecimal preco,
        boolean ativo
) implements Serializable {
}
