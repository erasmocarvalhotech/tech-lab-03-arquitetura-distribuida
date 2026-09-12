package com.techlab.estoque.service;

import com.techlab.estoque.dto.ReservationProcessedEvent.ItemIndisponivel;
import com.techlab.estoque.dto.ReservationRequestedEvent.ItemReserva;
import com.techlab.estoque.entity.SaldoEstoque;
import com.techlab.estoque.exception.ProdutoNaoEncontradoException;
import com.techlab.estoque.repository.SaldoEstoqueRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class SaldoEstoqueService {

    private final SaldoEstoqueRepository repository;

    public SaldoEstoqueService(SaldoEstoqueRepository repository) {
        this.repository = repository;
    }

    /**
     * Chamado ao consumir product-changed com event-type=product-created. Idempotente: se o saldo
     * ja existir (reentrega da mensagem), nao faz nada.
     */
    @Transactional
    public void criarSaldoZerado(Long produtoId) {
        if (repository.existsByProdutoId(produtoId)) {
            return;
        }
        repository.save(new SaldoEstoque(produtoId, 0));
    }

    /**
     * Entrada de estoque (carga inicial ou reposicao) - acao propria do estoque-service, separada
     * da criacao do produto (Decisao 4 do design.md).
     */
    @Transactional
    public void registrarEntrada(Long produtoId, int quantidade) {
        SaldoEstoque saldo = repository.findByProdutoId(produtoId)
                .orElseThrow(() -> new ProdutoNaoEncontradoException(produtoId));
        saldo.registrarEntrada(quantidade);
        repository.saveAndFlush(saldo);
    }

    /**
     * Valida disponibilidade em memoria e persiste via lock otimista (@Version). Estoque insuficiente
     * (sem conflito de escrita) e resultado de negocio, retornado normalmente. Conflito de concorrencia
     * (OptimisticLockingFailureException) propaga para o chamador, que deve deixar a mensagem ser
     * reciclada via fila -delayed (Decisao 3 do design.md) - nao e capturado aqui.
     */
    @Transactional
    public ResultadoReserva reservar(List<ItemReserva> itens) {
        List<ItemIndisponivel> indisponiveis = new ArrayList<>();
        List<SaldoEstoque> paraReservar = new ArrayList<>();

        for (ItemReserva item : itens) {
            SaldoEstoque saldo = repository.findByProdutoId(item.produtoId())
                    .orElseThrow(() -> new ProdutoNaoEncontradoException(item.produtoId()));
            if (saldo.temDisponibilidadePara(item.quantidade())) {
                paraReservar.add(saldo);
            } else {
                indisponiveis.add(new ItemIndisponivel(item.produtoId(), item.quantidade(), saldo.getQuantidadeDisponivel()));
            }
        }

        if (!indisponiveis.isEmpty()) {
            return ResultadoReserva.reservaRejeitada(indisponiveis);
        }

        for (int i = 0; i < itens.size(); i++) {
            paraReservar.get(i).reservar(itens.get(i).quantidade());
        }
        repository.saveAllAndFlush(paraReservar);

        return ResultadoReserva.reservaConfirmada();
    }
}
