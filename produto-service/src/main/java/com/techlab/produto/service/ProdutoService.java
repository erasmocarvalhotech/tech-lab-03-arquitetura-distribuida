package com.techlab.produto.service;

import com.techlab.produto.dto.ProdutoRequest;
import com.techlab.produto.dto.ProdutoUpdateRequest;
import com.techlab.produto.entity.Produto;
import com.techlab.produto.exception.ProdutoNaoEncontradoException;
import com.techlab.produto.exception.SkuDuplicadoException;
import com.techlab.produto.messaging.ProdutoChangedEvent;
import com.techlab.produto.messaging.TipoEventoProduto;
import com.techlab.produto.repository.ProdutoRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProdutoService {

    private static final Logger log = LoggerFactory.getLogger(ProdutoService.class);

    private final ProdutoRepository produtoRepository;
    private final ApplicationEventPublisher applicationEventPublisher;

    public ProdutoService(ProdutoRepository produtoRepository, ApplicationEventPublisher applicationEventPublisher) {
        this.produtoRepository = produtoRepository;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Transactional
    public Produto criar(ProdutoRequest request) {
        if (produtoRepository.existsBySku(request.sku())) {
            throw new SkuDuplicadoException(request.sku());
        }
        Produto produto = new Produto(request.nome(), request.descricao(), request.sku(), request.preco());
        produto = produtoRepository.save(produto);
        applicationEventPublisher.publishEvent(new ProdutoChangedEvent(produto, TipoEventoProduto.PRODUCT_CREATED));
        log.info("Produto criado: id={} sku={}", produto.getId(), produto.getSku());
        return produto;
    }

    @Transactional(readOnly = true)
    public Produto buscarPorId(Long id) {
        return produtoRepository.findById(id).orElseThrow(() -> new ProdutoNaoEncontradoException(id));
    }

    @Transactional(readOnly = true)
    public List<Produto> listar() {
        return produtoRepository.findAll();
    }

    @Transactional
    public Produto atualizar(Long id, ProdutoUpdateRequest request) {
        Produto produto = buscarPorId(id);
        produto.setNome(request.nome());
        produto.setDescricao(request.descricao());
        produto.setPreco(request.preco());
        produto = produtoRepository.save(produto);
        applicationEventPublisher.publishEvent(new ProdutoChangedEvent(produto, TipoEventoProduto.PRODUCT_UPDATED));
        log.info("Produto atualizado: id={} sku={}", produto.getId(), produto.getSku());
        return produto;
    }

    @Transactional
    public void inativar(Long id) {
        Produto produto = buscarPorId(id);
        produto.inativar();
        produto = produtoRepository.save(produto);
        applicationEventPublisher.publishEvent(new ProdutoChangedEvent(produto, TipoEventoProduto.PRODUCT_DEACTIVATED));
        log.info("Produto inativado: id={} sku={}", produto.getId(), produto.getSku());
    }
}
