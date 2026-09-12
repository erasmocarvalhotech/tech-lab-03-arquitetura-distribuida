package com.techlab.pedido.exception;

public class ProdutoIndisponivelException extends RuntimeException {

    public ProdutoIndisponivelException(Long produtoId) {
        super("Produto nao encontrado ou inativo no cache local para produtoId=" + produtoId);
    }
}
