package com.techlab.estoque.messaging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Cobre a Decisao 7 do design.md: 1 tentativa inicial + 2 retries via -delayed (3 operacoes no
 * total), depois publicacao manual na fila -failed.
 */
@ExtendWith(MockitoExtension.class)
class RetryPolicyTest {

    private static final String FILA_PRINCIPAL = "mb-techlab-stock-queue-reservation-requested";
    private static final String FILA_DELAYED = FILA_PRINCIPAL + "-delayed";
    private static final String FILA_FAILED = FILA_PRINCIPAL + "-failed";
    private static final long MAX_CICLOS_DELAYED = 2L;

    @Mock
    private RabbitTemplate rabbitTemplate;

    private RetryPolicy retryPolicy;

    @Test
    void primeiraTentativa_semXDeath_deveRelancarSemPublicarEmFailed() {
        retryPolicy = new RetryPolicy(new XDeathInspector(), rabbitTemplate, MAX_CICLOS_DELAYED);
        Message message = mensagemComCiclos(null);

        boolean esgotado = retryPolicy.tratarFalha(message, FILA_PRINCIPAL, new RuntimeException("falha simulada"));

        assertThat(esgotado).isFalse();
        verify(rabbitTemplate, never()).send(anyString(), anyString(), any());
    }

    @Test
    void segundaTentativa_umCicloJaFeito_deveRelancarSemPublicarEmFailed() {
        retryPolicy = new RetryPolicy(new XDeathInspector(), rabbitTemplate, MAX_CICLOS_DELAYED);
        Message message = mensagemComCiclos(1L);

        boolean esgotado = retryPolicy.tratarFalha(message, FILA_PRINCIPAL, new RuntimeException("falha simulada"));

        assertThat(esgotado).isFalse();
        verify(rabbitTemplate, never()).send(anyString(), anyString(), any());
    }

    @Test
    void terceiraTentativa_doisCiclosJaFeitos_devePublicarEmFailedENaoRelancar() {
        retryPolicy = new RetryPolicy(new XDeathInspector(), rabbitTemplate, MAX_CICLOS_DELAYED);
        Message message = mensagemComCiclos(2L);

        boolean esgotado = retryPolicy.tratarFalha(message, FILA_PRINCIPAL, new RuntimeException("falha simulada"));

        assertThat(esgotado).isTrue();
        verify(rabbitTemplate).send("", FILA_FAILED, message);
    }

    private Message mensagemComCiclos(Long count) {
        MessageProperties properties = new MessageProperties();
        if (count != null) {
            properties.setHeader("x-death", List.of(
                    Map.of("queue", FILA_DELAYED, "reason", "expired", "count", count)
            ));
        }
        return new Message(new byte[0], properties);
    }

}
