package com.techlab.pedido.dto;

import com.techlab.pedido.entity.ItemPedido;

import java.math.BigDecimal;

public record ItemPedidoResponse(
        Long produtoId,
        Integer quantidade,
        BigDecimal precoUnitario
) {
    public static ItemPedidoResponse from(ItemPedido item) {
        return new ItemPedidoResponse(item.getProdutoId(), item.getQuantidade(), item.getPrecoUnitario());
    }
}
