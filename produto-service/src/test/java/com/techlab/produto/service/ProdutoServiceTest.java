package com.techlab.produto.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.techlab.produto.dto.ProdutoRequest;
import com.techlab.produto.entity.Produto;
import com.techlab.produto.exception.SkuDuplicadoException;
import com.techlab.produto.messaging.ProdutoChangedEvent;
import com.techlab.produto.messaging.TipoEventoProduto;
import com.techlab.produto.repository.ProdutoRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class ProdutoServiceTest {

    @Mock
    private ProdutoRepository produtoRepository;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    private ProdutoService produtoService;

    @Test
    void criarPublicaEventoProductCreatedSemQuantidade() {
        produtoService = new ProdutoService(produtoRepository, applicationEventPublisher);
        ProdutoRequest request = new ProdutoRequest("Produto X", "desc", "ABC-001", new BigDecimal("19.90"));
        when(produtoRepository.existsBySku("ABC-001")).thenReturn(false);
        when(produtoRepository.save(any(Produto.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Produto produto = produtoService.criar(request);

        assertThat(produto.getSku()).isEqualTo("ABC-001");
        assertThat(produto.isAtivo()).isTrue();

        ArgumentCaptor<ProdutoChangedEvent> eventCaptor = ArgumentCaptor.forClass(ProdutoChangedEvent.class);
        verify(applicationEventPublisher).publishEvent(eventCaptor.capture());
        ProdutoChangedEvent event = eventCaptor.getValue();
        assertThat(event.tipo()).isEqualTo(TipoEventoProduto.PRODUCT_CREATED);
        assertThat(event.produto()).isSameAs(produto);
    }

    @Test
    void criarComSkuDuplicadoRejeitaSemPersistir() {
        produtoService = new ProdutoService(produtoRepository, applicationEventPublisher);
        ProdutoRequest request = new ProdutoRequest("Produto X", "desc", "ABC-001", new BigDecimal("19.90"));
        when(produtoRepository.existsBySku("ABC-001")).thenReturn(true);

        org.junit.jupiter.api.Assertions.assertThrows(
                SkuDuplicadoException.class, () -> produtoService.criar(request));
    }
}
