CREATE TABLE tb_saldo_estoque (
    id                     BIGSERIAL PRIMARY KEY,
    produto_id             BIGINT NOT NULL UNIQUE,
    quantidade_disponivel  INTEGER NOT NULL DEFAULT 0,
    quantidade_reservada   INTEGER NOT NULL DEFAULT 0,
    version                BIGINT NOT NULL DEFAULT 0,
    atualizado_em          TIMESTAMPTZ NOT NULL DEFAULT now()
);
