CREATE TABLE tb_pedido (
    id             BIGSERIAL PRIMARY KEY,
    status         VARCHAR(30) NOT NULL,
    criado_em      TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE tb_item_pedido (
    id              BIGSERIAL PRIMARY KEY,
    pedido_id       BIGINT NOT NULL REFERENCES tb_pedido(id),
    produto_id      BIGINT NOT NULL,
    quantidade      INTEGER NOT NULL,
    preco_unitario  NUMERIC(12,2) NOT NULL
);
