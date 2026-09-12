package com.techlab.estoque.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Garante que as chaves lidas via @Value nos componentes de mensageria (RabbitMqConfig,
 * ProductChangedListener, ReservationRequestedListener, EstoqueEventPublisher, RetryPolicy)
 * continuam resolvendo para os mesmos valores depois da migracao de application.properties
 * para application.yml.
 */
class ApplicationYmlTest {

    @Test
    void chavesDeMensageriaEDeRetryResolvemCorretamente() {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new ClassPathResource("application.yml"));
        Properties properties = factory.getObject();

        assertThat(properties.getProperty("server.port")).isEqualTo("8082");
        assertThat(properties.getProperty("spring.application.name")).isEqualTo("estoque-service");
        assertThat(properties.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        assertThat(properties.getProperty("spring.flyway.enabled")).isEqualTo("true");
        assertThat(properties.getProperty("spring.flyway.locations")).isEqualTo("classpath:db/migration");
        assertThat(properties.getProperty("spring.rabbitmq.listener.simple.default-requeue-rejected")).isEqualTo("false");

        assertThat(properties.getProperty("mb.techlab.stock.queue.product.changed"))
                .isEqualTo("mb-techlab-stock-queue-product-changed");
        assertThat(properties.getProperty("mb.techlab.stock.queue.reservation.requested"))
                .isEqualTo("mb-techlab-stock-queue-reservation-requested");
        assertThat(properties.getProperty("mb.techlab.stock.exchange.topic.reservation.processed"))
                .isEqualTo("mb-techlab-stock-exchange-topic-reservation-processed");
        assertThat(properties.getProperty("mb.techlab.stock.routing.key.reservation.processed"))
                .isEqualTo("reservation-processed");
        assertThat(properties.getProperty("mb.techlab.retry.max-death-count")).isEqualTo("2");
    }
}
