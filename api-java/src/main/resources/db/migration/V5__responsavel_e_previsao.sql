-- V5: responsavel pelo chamado e previsao de atendimento.
-- O sindico atribui o chamado a um sindico ou zelador (perfil ZELADOR, que nao precisa de coluna nova: o perfil e um texto)
-- e informa uma previsao. Quem NAO esta mais na equipe (desativado ou rebaixado) deixa de ser responsavel (ver UsuarioAdminService).

ALTER TABLE chamados ADD COLUMN responsavel_id BIGINT NULL;
ALTER TABLE chamados ADD COLUMN previsao DATE NULL;
ALTER TABLE chamados ADD CONSTRAINT fk_chamados_responsavel FOREIGN KEY (responsavel_id) REFERENCES usuarios (id);
CREATE INDEX idx_chamados_responsavel ON chamados (responsavel_id, status);
