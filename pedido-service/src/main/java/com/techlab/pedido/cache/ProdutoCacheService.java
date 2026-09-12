package com.techlab.pedido.cache;

import com.techlab.pedido.dto.ProdutoCacheDTO;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Projecao local (Redis) de dados de produto, alimentada exclusivamente pelo
 * listener do stream product-changed. Sem TTL - e projecao, nao cache-aside
 * (Decisao 5 e risco "cache desatualizado" do design).
 */
@Component
public class ProdutoCacheService {

    private static final String CHAVE_PREFIXO = "produto:";

    private final RedisTemplate<String, ProdutoCacheDTO> redisTemplate;

    public ProdutoCacheService(RedisTemplate<String, ProdutoCacheDTO> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void upsert(ProdutoCacheDTO produto) {
        redisTemplate.opsForValue().set(chave(produto.produtoId()), produto);
    }

    public void evict(Long produtoId) {
        redisTemplate.delete(chave(produtoId));
    }

    public Optional<ProdutoCacheDTO> buscar(Long produtoId) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(chave(produtoId)));
    }

    private String chave(Long produtoId) {
        return CHAVE_PREFIXO + produtoId;
    }
}
