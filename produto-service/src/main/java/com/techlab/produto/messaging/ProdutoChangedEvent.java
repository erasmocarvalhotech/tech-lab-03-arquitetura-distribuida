package com.techlab.produto.messaging;

import com.techlab.produto.entity.Produto;

public record ProdutoChangedEvent(Produto produto, TipoEventoProduto tipo) {
}
