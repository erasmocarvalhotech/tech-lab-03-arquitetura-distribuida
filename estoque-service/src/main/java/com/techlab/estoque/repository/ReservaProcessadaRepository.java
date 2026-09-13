package com.techlab.estoque.repository;

import com.techlab.estoque.entity.ReservaProcessada;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReservaProcessadaRepository extends JpaRepository<ReservaProcessada, Long> {

    Optional<ReservaProcessada> findByPedidoId(Long pedidoId);
}
