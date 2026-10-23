-- V9: recuperacao de senha por e-mail. Guarda so o HASH do token (quem ler o banco nao consegue usar o link).
CREATE TABLE IF NOT EXISTS tokens_senha (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    usuario_id  BIGINT      NOT NULL,
    token_hash  VARCHAR(64) NOT NULL,
    expira_em   TIMESTAMP   NOT NULL,
    usado_em    TIMESTAMP   NULL,
    criado_em   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_tokens_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id),
    CONSTRAINT uk_tokens_hash UNIQUE (token_hash),
    INDEX idx_tokens_usuario (usuario_id)
);
