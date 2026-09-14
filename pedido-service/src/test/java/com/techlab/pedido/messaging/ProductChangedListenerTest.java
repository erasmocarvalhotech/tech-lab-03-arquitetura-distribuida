package com.techlab.pedido.messaging;

import com.techlab.pedido.cache.ProdutoCacheService;
import com.techlab.pedido.dto.ProductChangedEvent;
import com.techlab.pedido.dto.ProdutoCacheDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductChangedListenerTest {

    private static final String FILA = "mb-techlab-order-queue-product-changed";

    @Mock
    private ProdutoCacheService produtoCacheService;

    @Mock
    private RetryPolicy retryPolicy;

    private ProductChangedListener listener;

    private final Message message = new Message(new byte[0], new MessageProperties());

    @Test
    void eventTypeProductCreated_atualizaCacheLocal() {
        listener = new ProductChangedListener(produtoCacheService, retryPolicy, FILA);
        ProductChangedEvent event = new ProductChangedEvent(
                "e1", OffsetDateTime.now(), 123L, "SKU-1", "Produto X", new BigDecimal("19.90"), true);

        listener.onProductChanged(event, message, "product-created");

        ArgumentCaptor<ProdutoCacheDTO> captor = ArgumentCaptor.forClass(ProdutoCacheDTO.class);
        verify(produtoCacheService).upsert(captor.capture());
        assertThat(captor.getValue().produtoId()).isEqualTo(123L);
        assertThat(captor.getValue().preco()).isEqualByComparingTo("19.90");
        assertThat(captor.getValue().occurredAt()).isEqualTo(event.occurredAt());
    }

    @Test
    void eventTypeProductUpdated_atualizaCacheLocal() {
        listener = new ProductChangedListener(produtoCacheService, retryPolicy, FILA);
        ProductChangedEvent event = new ProductChangedEvent(
                "e1", OffsetDateTime.now(), 123L, "SKU-1", "Produto X", new BigDecimal("24.90"), true);

        listener.onProductChanged(event, message, "product-updated");

        verify(produtoCacheService).upsert(new ProdutoCacheDTO(123L, "Produto X", new BigDecimal("24.90"), true, event.occurredAt()));
    }

    @Test
    void eventTypeProductDeactivated_removeDoCacheLocal() {
        listener = new ProductChangedListener(produtoCacheService, retryPolicy, FILA);
        ProductChangedEvent event = new ProductChangedEvent(
                "e1", OffsetDateTime.now(), 123L, "SKU-1", "Produto X", new BigDecimal("19.90"), false);

        listener.onProductChanged(event, message, "product-deactivated");

        verify(produtoCacheService).evict(123L);
        verify(produtoCacheService, never()).upsert(any());
    }

    @Test
    void eventoMaisAntigoQueOEstadoAtualDoCache_eDescartadoSemAtualizarNemRemover() {
        listener = new ProductChangedListener(produtoCacheService, retryPolicy, FILA);
        OffsetDateTime agora = OffsetDateTime.now();
        ProdutoCacheDTO cacheAtual = new ProdutoCacheDTO(123L, "Produto X", new BigDecimal("24.90"), true, agora);
        when(produtoCacheService.buscar(123L)).thenReturn(Optional.of(cacheAtual));
        ProductChangedEvent eventoAtrasado = new ProductChangedEvent(
                "e-antigo", agora.minusSeconds(5), 123L, "SKU-1", "Produto X", new BigDecimal("19.90"), true);

        listener.onProductChanged(eventoAtrasado, message, "product-updated");

        verify(produtoCacheService, never()).upsert(any());
    }

    @Test
    void eventoDeDesativacaoMaisAntigoQueOEstadoAtualDoCache_eDescartadoSemRemover() {
        listener = new ProductChangedListener(produtoCacheService, retryPolicy, FILA);
        OffsetDateTime agora = OffsetDateTime.now();
        ProdutoCacheDTO cacheAtual = new ProdutoCacheDTO(123L, "Produto X", new BigDecimal("24.90"), true, agora);
        when(produtoCacheService.buscar(123L)).thenReturn(Optional.of(cacheAtual));
        ProductChangedEvent desativacaoAtrasada = new ProductChangedEvent(
                "e-antigo", agora.minusSeconds(5), 123L, "SKU-1", "Produto X", new BigDecimal("19.90"), false);

        listener.onProductChanged(desativacaoAtrasada, message, "product-deactivated");

        verify(produtoCacheService, never()).evict(123L);
    }

    @Test
    void eventoMaisNovoQueOEstadoAtualDoCache_atualizaNormalmente() {
        listener = new ProductChangedListener(produtoCacheService, retryPolicy, FILA);
        OffsetDateTime agora = OffsetDateTime.now();
        ProdutoCacheDTO cacheAtual = new ProdutoCacheDTO(123L, "Produto X", new BigDecimal("19.90"), true, agora);
        when(produtoCacheService.buscar(123L)).thenReturn(Optional.of(cacheAtual));
        ProductChangedEvent eventoRecente = new ProductChangedEvent(
                "e-novo", agora.plusSeconds(5), 123L, "SKU-1", "Produto X", new BigDecimal("24.90"), true);

        listener.onProductChanged(eventoRecente, message, "product-updated");

        verify(produtoCacheService).upsert(new ProdutoCacheDTO(123L, "Produto X", new BigDecimal("24.90"), true, eventoRecente.occurredAt()));
    }

    @Test
    void falhaAoAtualizarCache_comCiclosDisponiveis_relancaExcecao() {
        listener = new ProductChangedListener(produtoCacheService, retryPolicy, FILA);
        ProductChangedEvent event = new ProductChangedEvent(
                "e1", OffsetDateTime.now(), 123L, "SKU-1", "Produto X", new BigDecimal("19.90"), true);
        RuntimeException falha = new RuntimeException("redis indisponivel");
        doThrow(falha).when(produtoCacheService).upsert(any());
        when(retryPolicy.tratarFalha(message, FILA, falha)).thenReturn(false);

        assertThatThrownBy(() -> listener.onProductChanged(event, message, "product-created"))
                .isSameAs(falha);
    }

    @Test
    void falhaAoAtualizarCache_comCiclosEsgotados_naoRelancaExcecao() {
        listener = new ProductChangedListener(produtoCacheService, retryPolicy, FILA);
        ProductChangedEvent event = new ProductChangedEvent(
                "e1", OffsetDateTime.now(), 123L, "SKU-1", "Produto X", new BigDecimal("19.90"), true);
        RuntimeException falha = new RuntimeException("redis indisponivel");
        doThrow(falha).when(produtoCacheService).upsert(any());
        when(retryPolicy.tratarFalha(message, FILA, falha)).thenReturn(true);

        listener.onProductChanged(event, message, "product-created");
    }

    private static ProdutoCacheDTO any() {
        return org.mockito.ArgumentMatchers.any();
    }
}
