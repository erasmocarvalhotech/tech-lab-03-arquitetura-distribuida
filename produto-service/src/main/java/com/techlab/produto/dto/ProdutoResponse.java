package com.techlab.produto.dto;

import com.techlab.produto.entity.Produto;
import java.math.BigDecimal;

public record ProdutoResponse(
        Long id,
        String nome,
        String descricao,
        String sku,
        BigDecimal preco,
        boolean ativo) {

    public static ProdutoResponse from(Produto produto) {
        return new ProdutoResponse(
                produto.getId(),
                produto.getNome(),
                produto.getDescricao(),
                produto.getSku(),
                produto.getPreco(),
                produto.isAtivo());
    }
}
