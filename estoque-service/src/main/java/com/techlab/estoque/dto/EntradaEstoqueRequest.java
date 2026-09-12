package com.techlab.estoque.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record EntradaEstoqueRequest(

        @NotNull
        Long produtoId,

        @NotNull
        @Min(1)
        Integer quantidade
) {
}
