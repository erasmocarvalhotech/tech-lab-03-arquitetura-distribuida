package com.techlab.estoque.messaging;

import org.springframework.amqp.core.Message;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Le o header x-death (preenchido automaticamente pelo RabbitMQ a cada dead-letter) para decidir,
 * junto com {@link RetryPolicy}, se uma falha deve reciclar pela fila -delayed ou ja cair na -failed.
 * Ver Decisao 7 em openspec/changes/arquitetura-microservicos/design.md.
 */
@Component
public class XDeathInspector {

    private static final String HEADER_X_DEATH = "x-death";
    private static final String REASON_EXPIRED = "expired";

    @SuppressWarnings("unchecked")
    public long countCiclosDelayed(Message message, String filaDelayed) {
        Object header = message.getMessageProperties().getHeaders().get(HEADER_X_DEATH);
        if (!(header instanceof List<?> entradas)) {
            return 0L;
        }
        for (Object entradaObj : entradas) {
            if (!(entradaObj instanceof Map<?, ?> entrada)) {
                continue;
            }
            Map<String, Object> death = (Map<String, Object>) entrada;
            String queue = valorComo(death.get("queue"));
            String reason = valorComo(death.get("reason"));
            if (filaDelayed.equals(queue) && REASON_EXPIRED.equals(reason)) {
                return contagem(death.get("count"));
            }
        }
        return 0L;
    }

    private long contagem(Object countValue) {
        return Optional.ofNullable(countValue)
                .map(v -> v instanceof Number number ? number.longValue() : Long.parseLong(v.toString()))
                .orElse(0L);
    }

    private String valorComo(Object value) {
        return value == null ? null : value.toString();
    }
}
