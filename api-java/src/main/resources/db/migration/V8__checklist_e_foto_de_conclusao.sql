-- V8: foto do servico pronto ("depois") e checklist de etapas por chamado.

ALTER TABLE chamados ADD COLUMN imagem_conclusao VARCHAR(100) NULL;

CREATE TABLE IF NOT EXISTS tarefas (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    chamado_id  BIGINT       NOT NULL,
    texto       VARCHAR(120) NOT NULL,
    feita       BOOLEAN      NOT NULL DEFAULT FALSE,
    ordem       INT          NOT NULL DEFAULT 0,
    CONSTRAINT fk_tarefas_chamado FOREIGN KEY (chamado_id) REFERENCES chamados (id),
    INDEX idx_tarefas_chamado (chamado_id, ordem)
);
