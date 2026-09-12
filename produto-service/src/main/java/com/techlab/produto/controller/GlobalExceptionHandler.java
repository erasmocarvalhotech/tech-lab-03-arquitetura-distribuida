package com.techlab.produto.controller;

import com.techlab.produto.dto.ErroResponse;
import com.techlab.produto.exception.ProdutoNaoEncontradoException;
import com.techlab.produto.exception.SkuDuplicadoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ProdutoNaoEncontradoException.class)
    public ResponseEntity<ErroResponse> handleProdutoNaoEncontrado(ProdutoNaoEncontradoException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErroResponse.of("PRODUTO_NAO_ENCONTRADO", ex.getMessage()));
    }

    @ExceptionHandler(SkuDuplicadoException.class)
    public ResponseEntity<ErroResponse> handleSkuDuplicado(SkuDuplicadoException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErroResponse.of("SKU_DUPLICADO", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErroResponse> handleValidacao(MethodArgumentNotValidException ex) {
        String mensagem = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(erro -> erro.getField() + ": " + erro.getDefaultMessage())
                .orElse("Dados inválidos");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErroResponse.of("VALIDACAO", mensagem));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErroResponse> handleErroInterno(Exception ex) {
        log.error("Erro inesperado ao processar a requisição", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErroResponse.of("ERRO_INTERNO", "Erro inesperado ao processar a requisição"));
    }
}
