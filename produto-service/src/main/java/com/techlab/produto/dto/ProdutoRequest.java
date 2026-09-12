package com.techlab.produto.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record ProdutoRequest(
        @NotBlank String nome,
        String descricao,
        @NotBlank String sku,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal preco) {
}
