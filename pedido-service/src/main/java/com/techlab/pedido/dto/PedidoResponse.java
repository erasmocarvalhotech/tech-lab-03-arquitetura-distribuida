package com.techlab.pedido.dto;

import com.techlab.pedido.entity.Pedido;

import java.util.List;

public record PedidoResponse(
        Long id,
        String status,
        List<ItemPedidoResponse> itens
) {
    public static PedidoResponse from(Pedido pedido) {
        List<ItemPedidoResponse> itens = pedido.getItens().stream()
                .map(ItemPedidoResponse::from)
                .toList();
        return new PedidoResponse(pedido.getId(), pedido.getStatus().name(), itens);
    }
}
