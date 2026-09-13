package com.techlab.estoque.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Entity
@Table(name = "tb_reserva_processada")
@Getter
@NoArgsConstructor
public class ReservaProcessada {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pedido_id", nullable = false, unique = true)
    private Long pedidoId;

    @Column(name = "resultado", nullable = false, length = 20)
    private String resultado;

    @Column(name = "itens_indisponiveis")
    private String itensIndisponiveis;

    @Column(name = "processado_em", nullable = false)
    private OffsetDateTime processadoEm;

    public ReservaProcessada(Long pedidoId, String resultado, String itensIndisponiveis) {
        this.pedidoId = pedidoId;
        this.resultado = resultado;
        this.itensIndisponiveis = itensIndisponiveis;
        this.processadoEm = OffsetDateTime.now();
    }
}
