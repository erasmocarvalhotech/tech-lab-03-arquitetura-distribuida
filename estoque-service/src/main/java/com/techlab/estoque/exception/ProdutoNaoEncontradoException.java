package com.techlab.estoque.exception;

public class ProdutoNaoEncontradoException extends RuntimeException {

    public ProdutoNaoEncontradoException(Long produtoId) {
        super("Saldo de estoque nao encontrado para produtoId=" + produtoId);
    }
}
