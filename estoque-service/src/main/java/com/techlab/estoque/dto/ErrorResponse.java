package com.techlab.estoque.dto;

import java.time.OffsetDateTime;

public record ErrorResponse(
        String codigo,
        String mensagem,
        OffsetDateTime timestamp
) {

    public static ErrorResponse of(String codigo, String mensagem) {
        return new ErrorResponse(codigo, mensagem, OffsetDateTime.now());
    }
}
