package com.techlab.pedido.exception;

import com.techlab.pedido.dto.ErroResponse;
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

    @ExceptionHandler(PedidoNaoEncontradoException.class)
    public ResponseEntity<ErroResponse> handlePedidoNaoEncontrado(PedidoNaoEncontradoException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErroResponse.of("PEDIDO_NAO_ENCONTRADO", ex.getMessage()));
    }

    @ExceptionHandler(ProdutoIndisponivelException.class)
    public ResponseEntity<ErroResponse> handleProdutoIndisponivel(ProdutoIndisponivelException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErroResponse.of("PRODUTO_INDISPONIVEL", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErroResponse> handleValidacao(MethodArgumentNotValidException ex) {
        String mensagem = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(erro -> erro.getField() + ": " + erro.getDefaultMessage())
                .orElse("Dados invalidos");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErroResponse.of("VALIDACAO", mensagem));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErroResponse> handleErroInterno(Exception ex) {
        log.error("Erro inesperado ao processar a requisicao", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErroResponse.of("ERRO_INTERNO", "Erro inesperado ao processar a requisicao"));
    }
}
