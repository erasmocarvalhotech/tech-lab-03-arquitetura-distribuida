package com.techlab.estoque.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Entity
@Table(name = "tb_saldo_estoque")
@Getter
@NoArgsConstructor
public class SaldoEstoque {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "produto_id", nullable = false, unique = true)
    private Long produtoId;

    @Column(name = "quantidade_disponivel", nullable = false)
    private Integer quantidadeDisponivel = 0;

    @Column(name = "quantidade_reservada", nullable = false)
    private Integer quantidadeReservada = 0;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "atualizado_em", nullable = false)
    private OffsetDateTime atualizadoEm;

    public SaldoEstoque(Long produtoId, Integer quantidadeDisponivel) {
        this.produtoId = produtoId;
        this.quantidadeDisponivel = quantidadeDisponivel;
        this.quantidadeReservada = 0;
        this.atualizadoEm = OffsetDateTime.now();
    }

    public void reservar(int quantidade) {
        this.quantidadeDisponivel -= quantidade;
        this.quantidadeReservada += quantidade;
        this.atualizadoEm = OffsetDateTime.now();
    }

    public void registrarEntrada(int quantidade) {
        this.quantidadeDisponivel += quantidade;
        this.atualizadoEm = OffsetDateTime.now();
    }

    public boolean temDisponibilidadePara(int quantidade) {
        return this.quantidadeDisponivel >= quantidade;
    }
}
