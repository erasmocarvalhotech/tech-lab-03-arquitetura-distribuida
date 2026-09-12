package com.techlab.produto.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.techlab.produto.entity.Produto;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

class ProdutoEventPublisherTest {

    private static final String EXCHANGE = "mb-techlab-product-exchange-topic-product-changed";
    private static final String ROUTING_KEY = "product-changed";

    @Test
    void publicaComHeaderEventTypeEPayloadSemQuantidade() throws Exception {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        ProdutoEventPublisher publisher = new ProdutoEventPublisher(rabbitTemplate, EXCHANGE, ROUTING_KEY);

        Produto produto = new Produto("Produto X", "desc", "ABC-001", new BigDecimal("19.90"));
        produto.setId(123L);

        publisher.onProdutoChanged(new ProdutoChangedEvent(produto, TipoEventoProduto.PRODUCT_CREATED));

        ArgumentCaptor<org.springframework.amqp.core.MessagePostProcessor> processorCaptor =
                ArgumentCaptor.forClass(org.springframework.amqp.core.MessagePostProcessor.class);
        verify(rabbitTemplate)
                .convertAndSend(eq(EXCHANGE), eq(ROUTING_KEY), any(ProdutoChangedMessage.class), processorCaptor.capture());

        Message message = new Message(new byte[0], new MessageProperties());
        Message processed = processorCaptor.getValue().postProcessMessage(message);
        String eventType = processed.getMessageProperties().getHeader("event-type");
        assertThat(eventType).isEqualTo("product-created");

        ProdutoChangedMessage payload = ProdutoChangedMessage.from(produto);
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        String json = objectMapper.writeValueAsString(payload);
        assertThat(json).doesNotContain("quantidade");
        assertThat(json).contains("\"produtoId\":123", "\"sku\":\"ABC-001\"", "\"ativo\":true");
    }
}
