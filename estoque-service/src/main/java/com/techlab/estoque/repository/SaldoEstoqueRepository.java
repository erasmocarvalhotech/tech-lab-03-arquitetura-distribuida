package com.techlab.estoque.repository;

import com.techlab.estoque.entity.SaldoEstoque;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SaldoEstoqueRepository extends JpaRepository<SaldoEstoque, Long> {

    Optional<SaldoEstoque> findByProdutoId(Long produtoId);

    boolean existsByProdutoId(Long produtoId);
}
