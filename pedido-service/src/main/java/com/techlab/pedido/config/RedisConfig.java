package com.techlab.pedido.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techlab.pedido.dto.ProdutoCacheDTO;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, ProdutoCacheDTO> produtoCacheRedisTemplate(
            RedisConnectionFactory connectionFactory, ObjectMapper objectMapper) {
        RedisTemplate<String, ProdutoCacheDTO> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new Jackson2JsonRedisSerializer<>(objectMapper, ProdutoCacheDTO.class));
        template.afterPropertiesSet();
        return template;
    }
}
