-- V4: conversa dentro do chamado. Morador e sindico trocam mensagens no proprio chamado (antes a comunicacao era
-- de mao unica: so uma observacao do sindico ao mudar o status).

CREATE TABLE IF NOT EXISTS mensagens (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    chamado_id  BIGINT        NOT NULL,
    autor_id    BIGINT        NOT NULL,
    texto       VARCHAR(1000) NOT NULL,
    criado_em   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_mensagens_chamado FOREIGN KEY (chamado_id) REFERENCES chamados (id),
    CONSTRAINT fk_mensagens_autor FOREIGN KEY (autor_id) REFERENCES usuarios (id),
    INDEX idx_mensagens_chamado (chamado_id, criado_em),
    INDEX idx_mensagens_autor (autor_id, criado_em)
);
