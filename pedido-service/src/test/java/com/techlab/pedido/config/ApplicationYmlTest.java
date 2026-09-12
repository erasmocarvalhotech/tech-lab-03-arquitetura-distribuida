package com.techlab.pedido.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Garante que as chaves lidas via @Value nos componentes de mensageria (RabbitMqConfig,
 * ProductChangedListener, ReservationProcessedListener, ReservationRequestedPublisher,
 * RetryPolicy) resolvem para os valores esperados pela topologia declarada em
 * infra/rabbitmq/definitions.json.
 */
class ApplicationYmlTest {

    @Test
    void chavesDeMensageriaEDeRetryResolvemCorretamente() {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new ClassPathResource("application.yml"));
        Properties properties = factory.getObject();

        assertThat(properties.getProperty("server.port")).isEqualTo("8083");
        assertThat(properties.getProperty("spring.application.name")).isEqualTo("pedido-service");
        assertThat(properties.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        assertThat(properties.getProperty("spring.flyway.enabled")).isEqualTo("true");
        assertThat(properties.getProperty("spring.flyway.locations")).isEqualTo("classpath:db/migration");
        assertThat(properties.getProperty("spring.rabbitmq.listener.simple.default-requeue-rejected")).isEqualTo("false");

        assertThat(properties.getProperty("mb.techlab.order.queue.product.changed"))
                .isEqualTo("mb-techlab-order-queue-product-changed");
        assertThat(properties.getProperty("mb.techlab.order.queue.reservation.processed"))
                .isEqualTo("mb-techlab-order-queue-reservation-processed");
        assertThat(properties.getProperty("mb.techlab.order.exchange.topic.reservation.requested"))
                .isEqualTo("mb-techlab-order-exchange-topic-reservation-requested");
        assertThat(properties.getProperty("mb.techlab.order.routing.key.reservation.requested"))
                .isEqualTo("reservation-requested");
        assertThat(properties.getProperty("mb.techlab.retry.max-death-count")).isEqualTo("2");
    }
}
