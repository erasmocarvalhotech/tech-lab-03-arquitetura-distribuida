package com.techlab.estoque.dto;

import com.techlab.estoque.entity.SaldoEstoque;

public record SaldoEstoqueResponse(
        Long produtoId,
        Integer quantidadeDisponivel,
        Integer quantidadeReservada
) {

    public static SaldoEstoqueResponse from(SaldoEstoque saldo) {
        return new SaldoEstoqueResponse(saldo.getProdutoId(), saldo.getQuantidadeDisponivel(), saldo.getQuantidadeReservada());
    }
}
