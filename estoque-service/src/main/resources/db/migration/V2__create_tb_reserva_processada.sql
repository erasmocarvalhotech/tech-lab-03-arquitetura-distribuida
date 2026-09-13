CREATE TABLE tb_reserva_processada (
    id                    BIGSERIAL PRIMARY KEY,
    pedido_id             BIGINT NOT NULL UNIQUE,
    resultado             VARCHAR(20) NOT NULL,
    itens_indisponiveis   TEXT,
    processado_em         TIMESTAMPTZ NOT NULL DEFAULT now()
);
