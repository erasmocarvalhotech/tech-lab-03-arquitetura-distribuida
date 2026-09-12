CREATE TABLE tb_produto (
    id             BIGSERIAL PRIMARY KEY,
    nome           VARCHAR(200) NOT NULL,
    descricao      VARCHAR(1000),
    sku            VARCHAR(50) NOT NULL UNIQUE,
    preco          NUMERIC(12,2) NOT NULL,
    ativo          BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em      TIMESTAMPTZ NOT NULL DEFAULT now(),
    atualizado_em  TIMESTAMPTZ NOT NULL DEFAULT now()
);
