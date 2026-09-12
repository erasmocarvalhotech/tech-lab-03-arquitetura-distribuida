package com.techlab.produto.repository;

import com.techlab.produto.entity.Produto;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProdutoRepository extends JpaRepository<Produto, Long> {

    boolean existsBySku(String sku);
}
