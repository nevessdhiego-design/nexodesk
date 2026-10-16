-- V3: emergencia. Chamado com risco a vida, a saude ou a seguranca (pessoa presa, choque, gas, fogo, alagamento)
-- fura a fila do sindico, independente da dificuldade. Marcada pelo morador ou detectada pelo servico Python.

ALTER TABLE chamados ADD COLUMN emergencia BOOLEAN NOT NULL DEFAULT FALSE;
