package com.techlab.pedido.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "tb_pedido")
@Getter
@NoArgsConstructor
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StatusPedido status;

    @Column(name = "criado_em", nullable = false)
    private OffsetDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private OffsetDateTime atualizadoEm;

    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<ItemPedido> itens = new ArrayList<>();

    public Pedido(List<ItemPedido> itens) {
        this.status = StatusPedido.PENDENTE;
        itens.forEach(this::adicionarItem);
    }

    private void adicionarItem(ItemPedido item) {
        item.vincularPedido(this);
        this.itens.add(item);
    }

    /**
     * Idempotente: so confirma se ainda estiver PENDENTE (reentrega de reservation-processed
     * nao deve reverter um pedido ja resolvido).
     */
    public void confirmar() {
        if (status == StatusPedido.PENDENTE) {
            this.status = StatusPedido.CONFIRMADO;
        }
    }

    public void rejeitarPorFaltaDeEstoque() {
        if (status == StatusPedido.PENDENTE) {
            this.status = StatusPedido.REJEITADO_SEM_ESTOQUE;
        }
    }

    @PrePersist
    void aoPersistir() {
        OffsetDateTime agora = OffsetDateTime.now();
        this.criadoEm = agora;
        this.atualizadoEm = agora;
    }

    @PreUpdate
    void aoAtualizar() {
        this.atualizadoEm = OffsetDateTime.now();
    }
}
