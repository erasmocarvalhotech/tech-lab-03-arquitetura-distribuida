package com.techlab.estoque.messaging;

import com.techlab.estoque.dto.ProductChangedEvent;
import com.techlab.estoque.service.SaldoEstoqueService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductChangedListenerTest {

    private static final String FILA = "mb-techlab-stock-queue-product-changed";

    @Mock
    private SaldoEstoqueService saldoEstoqueService;

    @Mock
    private RetryPolicy retryPolicy;

    private ProductChangedListener listener;

    private final Message message = new Message(new byte[0], new MessageProperties());

    @Test
    void eventTypeProductCreated_criaSaldoZerado() {
        listener = new ProductChangedListener(saldoEstoqueService, retryPolicy, FILA);
        ProductChangedEvent event = new ProductChangedEvent("e1", OffsetDateTime.now(), 123L, "SKU-1", "Produto X", null, true);

        listener.onProductChanged(event, message, "product-created");

        verify(saldoEstoqueService).criarSaldoZerado(123L);
    }

    @Test
    void eventTypeProductUpdated_naoChamaService() {
        listener = new ProductChangedListener(saldoEstoqueService, retryPolicy, FILA);
        ProductChangedEvent event = new ProductChangedEvent("e1", OffsetDateTime.now(), 123L, "SKU-1", "Produto X", null, true);

        listener.onProductChanged(event, message, "product-updated");

        verify(saldoEstoqueService, never()).criarSaldoZerado(123L);
    }

    @Test
    void falhaAoCriarSaldo_comCiclosDisponiveis_relancaExcecao() {
        listener = new ProductChangedListener(saldoEstoqueService, retryPolicy, FILA);
        ProductChangedEvent event = new ProductChangedEvent("e1", OffsetDateTime.now(), 123L, "SKU-1", "Produto X", null, true);
        RuntimeException falha = new RuntimeException("banco indisponivel");
        doThrow(falha).when(saldoEstoqueService).criarSaldoZerado(123L);
        when(retryPolicy.tratarFalha(message, FILA, falha)).thenReturn(false);

        assertThatThrownBy(() -> listener.onProductChanged(event, message, "product-created"))
                .isSameAs(falha);
    }

    @Test
    void falhaAoCriarSaldo_comCiclosEsgotados_naoRelancaExcecao() {
        listener = new ProductChangedListener(saldoEstoqueService, retryPolicy, FILA);
        ProductChangedEvent event = new ProductChangedEvent("e1", OffsetDateTime.now(), 123L, "SKU-1", "Produto X", null, true);
        RuntimeException falha = new RuntimeException("banco indisponivel");
        doThrow(falha).when(saldoEstoqueService).criarSaldoZerado(123L);
        when(retryPolicy.tratarFalha(message, FILA, falha)).thenReturn(true);

        listener.onProductChanged(event, message, "product-created");
    }
}
