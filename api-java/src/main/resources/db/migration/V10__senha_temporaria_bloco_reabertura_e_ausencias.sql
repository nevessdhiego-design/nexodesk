-- V10: senha temporaria que obriga a troca, bloco e apartamento estruturados, reabertura de chamado e ausencias da equipe.
ALTER TABLE usuarios ADD COLUMN senha_temporaria BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE usuarios ADD COLUMN bloco VARCHAR(20) NULL;
ALTER TABLE usuarios ADD COLUMN apartamento VARCHAR(10) NULL;
-- escala semanal do funcionario: dias da semana em que trabalha ("1,2,3,4,5" = segunda a sexta, 1 = segunda); nulo = todos os dias
ALTER TABLE usuarios ADD COLUMN dias_trabalho VARCHAR(20) NULL;

ALTER TABLE chamados ADD COLUMN reaberturas INT NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS ausencias (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    usuario_id  BIGINT       NOT NULL,
    tipo        VARCHAR(20)  NOT NULL,
    de          DATE         NOT NULL,
    ate         DATE         NOT NULL,
    observacao  VARCHAR(120) NULL,
    criado_em   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ausencias_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id),
    INDEX idx_ausencias_periodo (de, ate),
    INDEX idx_ausencias_usuario (usuario_id)
);
