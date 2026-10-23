-- V7: prazo (SLA) por categoria. Calculado na abertura; chamados antigos ficam sem prazo (nulo).
-- alerta_prazo_enviado: o aviso de prazo vencido sai uma unica vez.
ALTER TABLE chamados ADD COLUMN prazo_limite TIMESTAMP NULL;
ALTER TABLE chamados ADD COLUMN alerta_prazo_enviado BOOLEAN NOT NULL DEFAULT FALSE;
