-- V1: esquema base do NexoDesk (usuarios, chamados, historico_status, avaliacoes, notificacoes).
-- Funciona no H2 (modo MySQL, desenvolvimento) e no MySQL 8. No MySQL crie o banco antes:
--   CREATE DATABASE nexodesk CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
--
-- IMPORTANTE: este arquivo NUNCA deve ser editado depois de publicado (o Flyway guarda o checksum e
-- recusa subir). Toda mudanca no banco vira um arquivo novo: V2__descricao.sql, V3__..., e assim por diante.
-- Bancos criados antes do Flyway (pelo antigo schema.sql) entram com baseline na versao 1.

CREATE TABLE IF NOT EXISTS usuarios (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    nome        VARCHAR(120) NOT NULL,
    email       VARCHAR(160) NOT NULL,
    telefone    VARCHAR(20),
    unidade     VARCHAR(40),
    senha_hash  VARCHAR(100) NOT NULL,
    perfil      VARCHAR(20)  NOT NULL,
    ativo       BOOLEAN      NOT NULL DEFAULT TRUE,
    criado_em   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_usuarios_email UNIQUE (email)
);

CREATE TABLE IF NOT EXISTS chamados (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    titulo                  VARCHAR(150)  NOT NULL,
    descricao               VARCHAR(2000) NOT NULL,
    categoria               VARCHAR(30)   NOT NULL,
    local_ocorrencia        VARCHAR(120),
    anonimo                 BOOLEAN       NOT NULL DEFAULT FALSE,
    imagem                  VARCHAR(100),
    status                  VARCHAR(20)   NOT NULL,
    dificuldade             VARCHAR(10)   NOT NULL,
    pontuacao               INT           NOT NULL,
    justificativa           VARCHAR(1000),
    classificacao_fallback  BOOLEAN       NOT NULL DEFAULT FALSE,
    morador_id              BIGINT        NOT NULL,
    criado_em               TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_em           TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_chamados_morador FOREIGN KEY (morador_id) REFERENCES usuarios (id),
    INDEX idx_chamados_fila (status, pontuacao, criado_em),
    INDEX idx_chamados_morador (morador_id, criado_em)
);

CREATE TABLE IF NOT EXISTS historico_status (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    chamado_id       BIGINT       NOT NULL,
    status_anterior  VARCHAR(20),
    status_novo      VARCHAR(20)  NOT NULL,
    alterado_por_id  BIGINT       NOT NULL,
    observacao       VARCHAR(500),
    criado_em        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_historico_chamado FOREIGN KEY (chamado_id) REFERENCES chamados (id),
    CONSTRAINT fk_historico_usuario FOREIGN KEY (alterado_por_id) REFERENCES usuarios (id),
    INDEX idx_historico_chamado (chamado_id, criado_em)
);

-- Avaliação do atendimento: uma por chamado (UNIQUE), só do dono, só depois de RESOLVIDO (regra no serviço).
CREATE TABLE IF NOT EXISTS avaliacoes (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    chamado_id  BIGINT       NOT NULL,
    morador_id  BIGINT       NOT NULL,
    nota        INT          NOT NULL,
    comentario  VARCHAR(500),
    criado_em   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_avaliacoes_chamado UNIQUE (chamado_id),
    CONSTRAINT fk_avaliacoes_chamado FOREIGN KEY (chamado_id) REFERENCES chamados (id),
    CONSTRAINT fk_avaliacoes_morador FOREIGN KEY (morador_id) REFERENCES usuarios (id),
    CONSTRAINT ck_avaliacoes_nota CHECK (nota BETWEEN 1 AND 5)
);

CREATE TABLE IF NOT EXISTS notificacoes (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    usuario_id  BIGINT       NOT NULL,
    chamado_id  BIGINT       NOT NULL,
    mensagem    VARCHAR(300) NOT NULL,
    lida        BOOLEAN      NOT NULL DEFAULT FALSE,
    criado_em   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notificacoes_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id),
    CONSTRAINT fk_notificacoes_chamado FOREIGN KEY (chamado_id) REFERENCES chamados (id),
    INDEX idx_notificacoes_usuario (usuario_id, lida, criado_em)
);
