package com.techlab.pedido.service;

import com.techlab.pedido.cache.ProdutoCacheService;
import com.techlab.pedido.dto.ItemPedidoRequest;
import com.techlab.pedido.dto.PedidoRequest;
import com.techlab.pedido.dto.ProdutoCacheDTO;
import com.techlab.pedido.entity.ItemPedido;
import com.techlab.pedido.entity.Pedido;
import com.techlab.pedido.event.PedidoCriadoEvent;
import com.techlab.pedido.exception.PedidoNaoEncontradoException;
import com.techlab.pedido.exception.ProdutoIndisponivelException;
import com.techlab.pedido.repository.PedidoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PedidoService {

    private static final Logger log = LoggerFactory.getLogger(PedidoService.class);

    private final PedidoRepository pedidoRepository;
    private final ProdutoCacheService produtoCacheService;
    private final ApplicationEventPublisher applicationEventPublisher;

    public PedidoService(PedidoRepository pedidoRepository, ProdutoCacheService produtoCacheService,
                          ApplicationEventPublisher applicationEventPublisher) {
        this.pedidoRepository = pedidoRepository;
        this.produtoCacheService = produtoCacheService;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    /**
     * Le preco/ativo exclusivamente do cache local de produto (Decisao 5 do design.md) - nenhuma
     * chamada sincrona ao produto-service.
     */
    @Transactional
    public Pedido criar(PedidoRequest request) {
        Pedido pedido = new Pedido(request.itens().stream().map(this::paraItemPedido).toList());
        pedido = pedidoRepository.save(pedido);
        applicationEventPublisher.publishEvent(new PedidoCriadoEvent(pedido));
        log.info("Pedido criado: id={} itens={}", pedido.getId(), pedido.getItens().size());
        return pedido;
    }

    @Transactional(readOnly = true)
    public Pedido buscarPorId(Long id) {
        return pedidoRepository.findById(id).orElseThrow(() -> new PedidoNaoEncontradoException(id));
    }

    @Transactional
    public void confirmar(Long pedidoId) {
        pedidoRepository.findById(pedidoId).ifPresent(Pedido::confirmar);
    }

    @Transactional
    public void rejeitarPorFaltaDeEstoque(Long pedidoId) {
        pedidoRepository.findById(pedidoId).ifPresent(Pedido::rejeitarPorFaltaDeEstoque);
    }

    private ItemPedido paraItemPedido(ItemPedidoRequest itemRequest) {
        ProdutoCacheDTO produto = produtoCacheService.buscar(itemRequest.produtoId())
                .filter(ProdutoCacheDTO::ativo)
                .orElseThrow(() -> new ProdutoIndisponivelException(itemRequest.produtoId()));
        return new ItemPedido(itemRequest.produtoId(), itemRequest.quantidade(), produto.preco());
    }
}
