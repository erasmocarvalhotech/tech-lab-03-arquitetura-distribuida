package com.techlab.estoque.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techlab.estoque.dto.ReservationProcessedEvent;
import com.techlab.estoque.dto.ReservationProcessedEvent.ItemIndisponivel;
import com.techlab.estoque.dto.ReservationRequestedEvent.ItemReserva;
import com.techlab.estoque.entity.ReservaProcessada;
import com.techlab.estoque.entity.SaldoEstoque;
import com.techlab.estoque.exception.ProdutoNaoEncontradoException;
import com.techlab.estoque.repository.ReservaProcessadaRepository;
import com.techlab.estoque.repository.SaldoEstoqueRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class SaldoEstoqueService {

    private final SaldoEstoqueRepository repository;
    private final ReservaProcessadaRepository reservaProcessadaRepository;
    private final ObjectMapper objectMapper;

    public SaldoEstoqueService(SaldoEstoqueRepository repository,
                                ReservaProcessadaRepository reservaProcessadaRepository,
                                ObjectMapper objectMapper) {
        this.repository = repository;
        this.reservaProcessadaRepository = reservaProcessadaRepository;
        this.objectMapper = objectMapper;
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
     *
     * Idempotente por pedidoId (Decisao 1 do design.md de idempotencia-reserva-estoque): se aquele
     * pedidoId ja foi processado, retorna o resultado ja gravado sem tocar no saldo de novo. Caso
     * contrario, processa normalmente e grava o registro de idempotencia na mesma transacao.
     */
    @Transactional
    public ResultadoReserva reservar(Long pedidoId, List<ItemReserva> itens) {
        Optional<ReservaProcessada> jaProcessada = reservaProcessadaRepository.findByPedidoId(pedidoId);
        if (jaProcessada.isPresent()) {
            return paraResultado(jaProcessada.get());
        }

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

        ResultadoReserva resultado;
        if (!indisponiveis.isEmpty()) {
            resultado = ResultadoReserva.reservaRejeitada(indisponiveis);
        } else {
            for (int i = 0; i < itens.size(); i++) {
                paraReservar.get(i).reservar(itens.get(i).quantidade());
            }
            repository.saveAllAndFlush(paraReservar);
            resultado = ResultadoReserva.reservaConfirmada();
        }

        reservaProcessadaRepository.saveAndFlush(new ReservaProcessada(
                pedidoId,
                resultado.confirmada() ? ReservationProcessedEvent.CONFIRMED : ReservationProcessedEvent.REJECTED,
                escreverItensIndisponiveis(resultado.itensIndisponiveis())));

        return resultado;
    }

    private ResultadoReserva paraResultado(ReservaProcessada processada) {
        if (ReservationProcessedEvent.CONFIRMED.equals(processada.getResultado())) {
            return ResultadoReserva.reservaConfirmada();
        }
        return ResultadoReserva.reservaRejeitada(lerItensIndisponiveis(processada.getItensIndisponiveis()));
    }

    private String escreverItensIndisponiveis(List<ItemIndisponivel> itens) {
        if (itens.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(itens);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Falha ao serializar itens indisponiveis da reserva", ex);
        }
    }

    private List<ItemIndisponivel> lerItensIndisponiveis(String itensIndisponiveisJson) {
        if (itensIndisponiveisJson == null) {
            return List.of();
        }
        try {
            return objectMapper.readValue(itensIndisponiveisJson, new TypeReference<List<ItemIndisponivel>>() {
            });
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Falha ao desserializar itens indisponiveis da reserva", ex);
        }
    }
}
