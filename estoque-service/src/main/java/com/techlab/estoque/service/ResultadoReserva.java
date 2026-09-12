package com.techlab.estoque.service;

import com.techlab.estoque.dto.ReservationProcessedEvent.ItemIndisponivel;

import java.util.List;

public record ResultadoReserva(boolean confirmada, List<ItemIndisponivel> itensIndisponiveis) {

    public static ResultadoReserva reservaConfirmada() {
        return new ResultadoReserva(true, List.of());
    }

    public static ResultadoReserva reservaRejeitada(List<ItemIndisponivel> itensIndisponiveis) {
        return new ResultadoReserva(false, itensIndisponiveis);
    }
}
