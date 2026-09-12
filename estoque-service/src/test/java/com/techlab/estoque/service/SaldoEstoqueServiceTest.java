package com.techlab.estoque.service;

import com.techlab.estoque.dto.ReservationRequestedEvent.ItemReserva;
import com.techlab.estoque.entity.SaldoEstoque;
import com.techlab.estoque.exception.ProdutoNaoEncontradoException;
import com.techlab.estoque.repository.SaldoEstoqueRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SaldoEstoqueServiceTest {

    @Mock
    private SaldoEstoqueRepository repository;

    @InjectMocks
    private SaldoEstoqueService service;

    @Test
    void reservarComSaldoSuficiente_decrementaDisponivelEIncrementaReservado() {
        SaldoEstoque saldo = new SaldoEstoque(123L, 10);
        when(repository.findByProdutoId(123L)).thenReturn(Optional.of(saldo));

        ResultadoReserva resultado = service.reservar(List.of(new ItemReserva(123L, 4)));

        assertThat(resultado.confirmada()).isTrue();
        assertThat(saldo.getQuantidadeDisponivel()).isEqualTo(6);
        assertThat(saldo.getQuantidadeReservada()).isEqualTo(4);
        verify(repository).saveAllAndFlush(List.of(saldo));
    }

    @Test
    void reservarComEstoqueInsuficiente_retornaRejeitadaSemAlterarSaldoNemSalvar() {
        SaldoEstoque saldo = new SaldoEstoque(123L, 2);
        when(repository.findByProdutoId(123L)).thenReturn(Optional.of(saldo));

        ResultadoReserva resultado = service.reservar(List.of(new ItemReserva(123L, 5)));

        assertThat(resultado.confirmada()).isFalse();
        assertThat(resultado.itensIndisponiveis()).hasSize(1);
        assertThat(resultado.itensIndisponiveis().get(0).produtoId()).isEqualTo(123L);
        assertThat(resultado.itensIndisponiveis().get(0).quantidadeDisponivel()).isEqualTo(2);
        assertThat(saldo.getQuantidadeDisponivel()).isEqualTo(2);
        verify(repository, never()).saveAllAndFlush(any());
    }

    @Test
    void reservarProdutoInexistente_lancaProdutoNaoEncontrado() {
        when(repository.findByProdutoId(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reservar(List.of(new ItemReserva(999L, 1))))
                .isInstanceOf(ProdutoNaoEncontradoException.class);
    }

    @Test
    void criarSaldoZerado_idempotente_naoDuplicaQuandoJaExiste() {
        when(repository.existsByProdutoId(123L)).thenReturn(true);

        service.criarSaldoZerado(123L);

        verify(repository, never()).save(any());
    }

    @Test
    void criarSaldoZerado_criaComQuantidadeZeroQuandoNaoExiste() {
        when(repository.existsByProdutoId(123L)).thenReturn(false);

        service.criarSaldoZerado(123L);

        verify(repository, times(1)).save(any(SaldoEstoque.class));
    }

    @Test
    void registrarEntrada_incrementaDisponivel() {
        SaldoEstoque saldo = new SaldoEstoque(123L, 5);
        when(repository.findByProdutoId(123L)).thenReturn(Optional.of(saldo));

        service.registrarEntrada(123L, 10);

        assertThat(saldo.getQuantidadeDisponivel()).isEqualTo(15);
        verify(repository).saveAndFlush(saldo);
    }

    @Test
    void registrarEntradaParaProdutoSemSaldo_lancaProdutoNaoEncontrado() {
        when(repository.findByProdutoId(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.registrarEntrada(999L, 10))
                .isInstanceOf(ProdutoNaoEncontradoException.class);
    }
}
