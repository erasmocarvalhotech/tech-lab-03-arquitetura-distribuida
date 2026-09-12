package com.techlab.produto.exception;

public class SkuDuplicadoException extends RuntimeException {

    public SkuDuplicadoException(String sku) {
        super("SKU já cadastrado: " + sku);
    }
}
