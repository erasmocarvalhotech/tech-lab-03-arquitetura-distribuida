package com.techlab.estoque.controller;

import com.techlab.estoque.dto.EntradaEstoqueRequest;
import com.techlab.estoque.dto.SaldoEstoqueResponse;
import com.techlab.estoque.entity.SaldoEstoque;
import com.techlab.estoque.exception.ProdutoNaoEncontradoException;
import com.techlab.estoque.repository.SaldoEstoqueRepository;
import com.techlab.estoque.service.SaldoEstoqueService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/estoque")
public class EstoqueController {

    private final SaldoEstoqueService saldoEstoqueService;
    private final SaldoEstoqueRepository saldoEstoqueRepository;

    public EstoqueController(SaldoEstoqueService saldoEstoqueService, SaldoEstoqueRepository saldoEstoqueRepository) {
        this.saldoEstoqueService = saldoEstoqueService;
        this.saldoEstoqueRepository = saldoEstoqueRepository;
    }

    @PostMapping("/entradas")
    public ResponseEntity<SaldoEstoqueResponse> registrarEntrada(@Valid @RequestBody EntradaEstoqueRequest request) {
        saldoEstoqueService.registrarEntrada(request.produtoId(), request.quantidade());
        SaldoEstoque saldo = saldoEstoqueRepository.findByProdutoId(request.produtoId())
                .orElseThrow(() -> new ProdutoNaoEncontradoException(request.produtoId()));
        return ResponseEntity.status(HttpStatus.CREATED).body(SaldoEstoqueResponse.from(saldo));
    }

    @GetMapping("/{produtoId}")
    public ResponseEntity<SaldoEstoqueResponse> consultar(@PathVariable Long produtoId) {
        SaldoEstoque saldo = saldoEstoqueRepository.findByProdutoId(produtoId)
                .orElseThrow(() -> new ProdutoNaoEncontradoException(produtoId));
        return ResponseEntity.ok(SaldoEstoqueResponse.from(saldo));
    }
}
