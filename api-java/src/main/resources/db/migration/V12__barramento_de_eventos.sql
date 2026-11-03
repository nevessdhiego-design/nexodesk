-- V12: barramento de eventos entre instancias da API (tempo real com mais de uma instancia).
-- Cada instancia grava aqui o aviso que gerou (so tipo e ids, nunca conteudo) e le o que as OUTRAS gravaram.
-- As linhas sao apagadas depois de alguns minutos.
CREATE TABLE IF NOT EXISTS eventos_tempo_real (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    instancia    VARCHAR(40)  NOT NULL,
    destinatario VARCHAR(160) NOT NULL,
    tipo         VARCHAR(30)  NOT NULL,
    chamado_id   BIGINT       NOT NULL,
    criado_em    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_eventos_criado (criado_em)
);
