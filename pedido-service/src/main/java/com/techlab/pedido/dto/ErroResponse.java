package com.techlab.pedido.dto;

import java.time.OffsetDateTime;

public record ErroResponse(String codigo, String mensagem, OffsetDateTime timestamp) {

    public static ErroResponse of(String codigo, String mensagem) {
        return new ErroResponse(codigo, mensagem, OffsetDateTime.now());
    }
}
