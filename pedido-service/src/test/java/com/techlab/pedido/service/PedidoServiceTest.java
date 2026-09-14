package com.techlab.pedido.service;

import com.techlab.pedido.cache.ProdutoCacheService;
import com.techlab.pedido.dto.ItemPedidoRequest;
import com.techlab.pedido.dto.PedidoRequest;
import com.techlab.pedido.dto.ProdutoCacheDTO;
import com.techlab.pedido.entity.Pedido;
import com.techlab.pedido.entity.StatusPedido;
import com.techlab.pedido.event.PedidoCriadoEvent;
import com.techlab.pedido.exception.PedidoNaoEncontradoException;
import com.techlab.pedido.exception.ProdutoIndisponivelException;
import com.techlab.pedido.repository.PedidoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PedidoServiceTest {

    @Mock
    private PedidoRepository pedidoRepository;

    @Mock
    private ProdutoCacheService produtoCacheService;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @InjectMocks
    private PedidoService pedidoService;

    @Test
    void criar_usaPrecoDoCacheLocalEPublicaPedidoCriadoEvent() {
        Long produtoId = 123L;
        ProdutoCacheDTO produtoCache = new ProdutoCacheDTO(produtoId, "Produto X", new BigDecimal("19.90"), true, OffsetDateTime.now());
        when(produtoCacheService.buscar(produtoId)).thenReturn(Optional.of(produtoCache));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PedidoRequest request = new PedidoRequest(List.of(new ItemPedidoRequest(produtoId, 2)));

        Pedido pedido = pedidoService.criar(request);

        assertThat(pedido.getStatus()).isEqualTo(StatusPedido.PENDENTE);
        assertThat(pedido.getItens()).hasSize(1);
        assertThat(pedido.getItens().get(0).getPrecoUnitario()).isEqualByComparingTo("19.90");
        verify(applicationEventPublisher).publishEvent(any(PedidoCriadoEvent.class));
    }

    @Test
    void criar_rejeitaQuandoProdutoAusenteNoCacheLocal() {
        Long produtoId = 999L;
        when(produtoCacheService.buscar(produtoId)).thenReturn(Optional.empty());

        PedidoRequest request = new PedidoRequest(List.of(new ItemPedidoRequest(produtoId, 1)));

        assertThatThrownBy(() -> pedidoService.criar(request))
                .isInstanceOf(ProdutoIndisponivelException.class);
    }

    @Test
    void criar_rejeitaQuandoProdutoInativoNoCacheLocal() {
        Long produtoId = 321L;
        ProdutoCacheDTO produtoInativo = new ProdutoCacheDTO(produtoId, "Produto Inativo", new BigDecimal("10.00"), false, OffsetDateTime.now());
        when(produtoCacheService.buscar(produtoId)).thenReturn(Optional.of(produtoInativo));

        PedidoRequest request = new PedidoRequest(List.of(new ItemPedidoRequest(produtoId, 1)));

        assertThatThrownBy(() -> pedidoService.criar(request))
                .isInstanceOf(ProdutoIndisponivelException.class);
    }

    @Test
    void buscarPorId_lancaExcecaoQuandoPedidoNaoExiste() {
        when(pedidoRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pedidoService.buscarPorId(1L))
                .isInstanceOf(PedidoNaoEncontradoException.class);
    }

    @Test
    void confirmar_alteraStatusDePedidoPendente() {
        Pedido pedido = new Pedido(List.of());
        when(pedidoRepository.findById(1L)).thenReturn(Optional.of(pedido));

        pedidoService.confirmar(1L);

        assertThat(pedido.getStatus()).isEqualTo(StatusPedido.CONFIRMADO);
    }

    @Test
    void rejeitarPorFaltaDeEstoque_alteraStatusDePedidoPendente() {
        Pedido pedido = new Pedido(List.of());
        when(pedidoRepository.findById(1L)).thenReturn(Optional.of(pedido));

        pedidoService.rejeitarPorFaltaDeEstoque(1L);

        assertThat(pedido.getStatus()).isEqualTo(StatusPedido.REJEITADO_SEM_ESTOQUE);
    }

    @Test
    void rejeitarPorFaltaDeEstoque_naoAlteraPedidoJaResolvido() {
        Pedido pedido = new Pedido(List.of());
        pedido.confirmar();
        when(pedidoRepository.findById(1L)).thenReturn(Optional.of(pedido));

        pedidoService.rejeitarPorFaltaDeEstoque(1L);

        assertThat(pedido.getStatus()).isEqualTo(StatusPedido.CONFIRMADO);
    }
}
