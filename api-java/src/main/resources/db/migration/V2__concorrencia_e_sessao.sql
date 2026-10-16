-- V2: dois controles de seguranca/consistencia.
--
-- chamados.versao: controle de concorrencia otimista. Cada alteracao soma 1. Se duas pessoas editam o mesmo
-- chamado a partir de telas desatualizadas, a segunda recebe 409 em vez de apagar a primeira em silencio.
--
-- usuarios.versao_sessao: trocar a senha (ou desativar o usuario) invalida as sessoes abertas. A sessao
-- guarda a versao do login e a API compara a cada requisicao.

ALTER TABLE chamados ADD COLUMN versao BIGINT NOT NULL DEFAULT 0;
ALTER TABLE usuarios ADD COLUMN versao_sessao INT NOT NULL DEFAULT 0;
