package com.techlab.estoque.messaging;

import com.techlab.estoque.dto.ProductChangedEvent;
import com.techlab.estoque.service.SaldoEstoqueService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
public class ProductChangedListener {

    private static final Logger log = LoggerFactory.getLogger(ProductChangedListener.class);
    private static final String EVENT_TYPE_CREATED = "product-created";

    private final SaldoEstoqueService saldoEstoqueService;
    private final RetryPolicy retryPolicy;
    private final String filaPrincipal;

    public ProductChangedListener(SaldoEstoqueService saldoEstoqueService, RetryPolicy retryPolicy,
                                   @Value("${mb.techlab.stock.queue.product.changed}") String filaPrincipal) {
        this.saldoEstoqueService = saldoEstoqueService;
        this.retryPolicy = retryPolicy;
        this.filaPrincipal = filaPrincipal;
    }

    @RabbitListener(queues = "${mb.techlab.stock.queue.product.changed}")
    public void onProductChanged(ProductChangedEvent event, Message message,
                                  @Header(name = "event-type", required = false) String eventType) {
        try {
            if (EVENT_TYPE_CREATED.equals(eventType)) {
                saldoEstoqueService.criarSaldoZerado(event.produtoId());
                log.info("Saldo de estoque criado: produtoId={} sku={}", event.produtoId(), event.sku());
            } else {
                log.debug("event-type={} ignorado pelo estoque-service (produtoId={})", eventType, event.produtoId());
            }
        } catch (Exception ex) {
            boolean esgotado = retryPolicy.tratarFalha(message, filaPrincipal, ex);
            if (!esgotado) {
                throw ex;
            }
        }
    }
}
