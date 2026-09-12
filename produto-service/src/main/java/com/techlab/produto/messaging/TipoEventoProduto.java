package com.techlab.produto.messaging;

public enum TipoEventoProduto {
    PRODUCT_CREATED("product-created"),
    PRODUCT_UPDATED("product-updated"),
    PRODUCT_DEACTIVATED("product-deactivated");

    private final String eventType;

    TipoEventoProduto(String eventType) {
        this.eventType = eventType;
    }

    public String eventType() {
        return eventType;
    }
}
