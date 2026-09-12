package com.techlab.pedido.exception;

public class PedidoNaoEncontradoException extends RuntimeException {

    public PedidoNaoEncontradoException(Long pedidoId) {
        super("Pedido nao encontrado para id=" + pedidoId);
    }
}
