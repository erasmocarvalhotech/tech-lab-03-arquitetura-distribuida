package com.techlab.pedido.messaging;

import com.techlab.pedido.cache.ProdutoCacheService;
import com.techlab.pedido.dto.ProductChangedEvent;
import com.techlab.pedido.dto.ProdutoCacheDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Consome mb-techlab-order-queue-product-changed e mantem a projecao local de produto (Decisao 5
 * do design.md). Fila propria do pedido-service, independente da consumida pelo estoque-service.
 */
@Component
public class ProductChangedListener {

    private static final Logger log = LoggerFactory.getLogger(ProductChangedListener.class);

    private static final String EVENT_TYPE_CREATED = "product-created";
    private static final String EVENT_TYPE_UPDATED = "product-updated";
    private static final String EVENT_TYPE_DEACTIVATED = "product-deactivated";

    private final ProdutoCacheService produtoCacheService;
    private final RetryPolicy retryPolicy;
    private final String filaPrincipal;

    public ProductChangedListener(ProdutoCacheService produtoCacheService, RetryPolicy retryPolicy,
                                   @Value("${mb.techlab.order.queue.product.changed}") String filaPrincipal) {
        this.produtoCacheService = produtoCacheService;
        this.retryPolicy = retryPolicy;
        this.filaPrincipal = filaPrincipal;
    }

    @RabbitListener(queues = "${mb.techlab.order.queue.product.changed}")
    public void onProductChanged(ProductChangedEvent event, Message message,
                                  @Header(name = "event-type", required = false) String eventType) {
        try {
            aplicarEvento(eventType, event);
        } catch (Exception ex) {
            boolean esgotado = retryPolicy.tratarFalha(message, filaPrincipal, ex);
            if (!esgotado) {
                throw ex;
            }
        }
    }

    private void aplicarEvento(String eventType, ProductChangedEvent event) {
        boolean reconhecido = EVENT_TYPE_CREATED.equals(eventType) || EVENT_TYPE_UPDATED.equals(eventType)
                || EVENT_TYPE_DEACTIVATED.equals(eventType);
        if (!reconhecido) {
            log.debug("event-type={} ignorado pelo pedido-service (produtoId={})", eventType, event.produtoId());
            return;
        }

        if (eventoDesatualizado(event)) {
            log.debug("Evento product-changed descartado por estar desatualizado: produtoId={} eventType={} occurredAt={}",
                    event.produtoId(), eventType, event.occurredAt());
            return;
        }

        if (EVENT_TYPE_CREATED.equals(eventType) || EVENT_TYPE_UPDATED.equals(eventType)) {
            produtoCacheService.upsert(new ProdutoCacheDTO(event.produtoId(), event.nome(), event.preco(), event.ativo(), event.occurredAt()));
            log.info("Cache local de produto atualizado: produtoId={} eventType={}", event.produtoId(), eventType);
        } else {
            produtoCacheService.evict(event.produtoId());
            log.info("Cache local de produto removido: produtoId={}", event.produtoId());
        }
    }

    /**
     * Mitigacao do risco "evento fora de ordem" (design.md da spec arquitetura-microservicos):
     * um evento nao mais recente que o occurredAt ja aplicado no cache local e descartado, para
     * que uma reentrega ou entrega fora de ordem no stream product-changed nao sobrescreva um
     * estado mais novo com um mais antigo.
     */
    private boolean eventoDesatualizado(ProductChangedEvent event) {
        return produtoCacheService.buscar(event.produtoId())
                .map(ProdutoCacheDTO::occurredAt)
                .map(occurredAtAtual -> !event.occurredAt().isAfter(occurredAtAtual))
                .orElse(false);
    }
}
