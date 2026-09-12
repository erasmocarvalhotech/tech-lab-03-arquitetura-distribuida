package com.techlab.estoque.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Aplica a Decisao 7 do design.md: 1 tentativa inicial + 2 retries via fila -delayed (3 operacoes
 * no total), depois publicacao manual na fila -failed. RabbitMQ ja recicla a mensagem entre a fila
 * principal e a -delayed via DLX declarado em infra/rabbitmq/definitions.json; este componente so
 * decide, a partir do x-death, quando esse ciclo deve parar.
 */
@Component
public class RetryPolicy {

    private static final Logger log = LoggerFactory.getLogger(RetryPolicy.class);

    private final XDeathInspector xDeathInspector;
    private final RabbitTemplate rabbitTemplate;
    private final long maxCiclosDelayed;

    public RetryPolicy(XDeathInspector xDeathInspector, RabbitTemplate rabbitTemplate,
                        @Value("${mb.techlab.retry.max-death-count:2}") long maxCiclosDelayed) {
        this.xDeathInspector = xDeathInspector;
        this.rabbitTemplate = rabbitTemplate;
        this.maxCiclosDelayed = maxCiclosDelayed;
    }

    /**
     * @return true se a mensagem foi publicada manualmente na fila -failed (consumidor deve fazer
     *         ack normal, sem relancar a excecao); false se o consumidor deve relancar a excecao
     *         para que o broker faca dead-letter na fila -delayed (mais um ciclo de retry).
     */
    public boolean tratarFalha(Message message, String filaPrincipal, Exception causa) {
        String filaDelayed = filaPrincipal + "-delayed";
        String filaFailed = filaPrincipal + "-failed";
        long ciclosJaExecutados = xDeathInspector.countCiclosDelayed(message, filaDelayed);
        if (ciclosJaExecutados >= maxCiclosDelayed) {
            log.warn("Limite de retentativas esgotado para fila {} (ciclos={}, causa={}), publicando em {}",
                    filaPrincipal, ciclosJaExecutados, causa.getMessage(), filaFailed);
            rabbitTemplate.send("", filaFailed, message);
            return true;
        }
        log.warn("Falha ao processar mensagem de {} (ciclo {}/{}), reciclando via {}: {}",
                filaPrincipal, ciclosJaExecutados, maxCiclosDelayed, filaDelayed, causa.getMessage());
        return false;
    }
}
