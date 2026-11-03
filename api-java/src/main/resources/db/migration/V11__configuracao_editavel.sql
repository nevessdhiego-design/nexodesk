-- V11: matriz funcao x categoria e modelos de checklist EDITAVEIS pelo sindico (antes fixos no codigo).
-- Os valores abaixo sao o padrao de fabrica (iguais a Funcao e ChecklistModelo; um teste confere).

CREATE TABLE IF NOT EXISTS funcao_categorias (
    funcao    VARCHAR(30) NOT NULL,
    categoria VARCHAR(30) NOT NULL,
    PRIMARY KEY (funcao, categoria)
);

CREATE TABLE IF NOT EXISTS checklist_modelos (
    id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    categoria VARCHAR(30)  NOT NULL,
    ordem     INT          NOT NULL,
    texto     VARCHAR(120) NOT NULL,
    INDEX idx_checklist_categoria (categoria, ordem)
);

INSERT INTO funcao_categorias (funcao, categoria) VALUES ('ZELADOR', 'LIMPEZA');
INSERT INTO funcao_categorias (funcao, categoria) VALUES ('ZELADOR', 'AREAS_COMUNS');
INSERT INTO funcao_categorias (funcao, categoria) VALUES ('ZELADOR', 'ESTACIONAMENTO');
INSERT INTO funcao_categorias (funcao, categoria) VALUES ('ZELADOR', 'OUTROS');
INSERT INTO funcao_categorias (funcao, categoria) VALUES ('FAXINEIRO', 'LIMPEZA');
INSERT INTO funcao_categorias (funcao, categoria) VALUES ('FAXINEIRO', 'AREAS_COMUNS');
INSERT INTO funcao_categorias (funcao, categoria) VALUES ('ELETRICISTA', 'ELETRICA');
INSERT INTO funcao_categorias (funcao, categoria) VALUES ('ELETRICISTA', 'ILUMINACAO');
INSERT INTO funcao_categorias (funcao, categoria) VALUES ('ENCANADOR', 'HIDRAULICA');
INSERT INTO funcao_categorias (funcao, categoria) VALUES ('MANUTENCAO', 'ESTRUTURA');
INSERT INTO funcao_categorias (funcao, categoria) VALUES ('MANUTENCAO', 'AREAS_COMUNS');
INSERT INTO funcao_categorias (funcao, categoria) VALUES ('MANUTENCAO', 'OUTROS');
INSERT INTO funcao_categorias (funcao, categoria) VALUES ('PORTEIRO', 'PORTARIA');
INSERT INTO funcao_categorias (funcao, categoria) VALUES ('PORTEIRO', 'ESTACIONAMENTO');
INSERT INTO funcao_categorias (funcao, categoria) VALUES ('PORTEIRO', 'BARULHO');
INSERT INTO funcao_categorias (funcao, categoria) VALUES ('PORTEIRO', 'CONVIVENCIA');
INSERT INTO funcao_categorias (funcao, categoria) VALUES ('SEGURANCA', 'SEGURANCA');
INSERT INTO funcao_categorias (funcao, categoria) VALUES ('SEGURANCA', 'BARULHO');
INSERT INTO funcao_categorias (funcao, categoria) VALUES ('TECNICO_EXTERNO', 'ELEVADOR');

INSERT INTO checklist_modelos (categoria, ordem, texto) VALUES ('LIMPEZA', 0, 'Recolher o lixo e os resíduos');
INSERT INTO checklist_modelos (categoria, ordem, texto) VALUES ('LIMPEZA', 1, 'Varrer ou lavar o local');
INSERT INTO checklist_modelos (categoria, ordem, texto) VALUES ('LIMPEZA', 2, 'Secar o piso para evitar escorregões');
INSERT INTO checklist_modelos (categoria, ordem, texto) VALUES ('LIMPEZA', 3, 'Conferir se ficou limpo');
INSERT INTO checklist_modelos (categoria, ordem, texto) VALUES ('ILUMINACAO', 0, 'Conferir qual lâmpada ou luminária está com defeito');
INSERT INTO checklist_modelos (categoria, ordem, texto) VALUES ('ILUMINACAO', 1, 'Trocar a lâmpada ou o reator');
INSERT INTO checklist_modelos (categoria, ordem, texto) VALUES ('ILUMINACAO', 2, 'Testar o funcionamento');
INSERT INTO checklist_modelos (categoria, ordem, texto) VALUES ('ELETRICA', 0, 'Desligar o disjuntor do trecho');
INSERT INTO checklist_modelos (categoria, ordem, texto) VALUES ('ELETRICA', 1, 'Localizar o defeito');
INSERT INTO checklist_modelos (categoria, ordem, texto) VALUES ('ELETRICA', 2, 'Consertar ou trocar o componente');
INSERT INTO checklist_modelos (categoria, ordem, texto) VALUES ('ELETRICA', 3, 'Religar e testar');
INSERT INTO checklist_modelos (categoria, ordem, texto) VALUES ('ELETRICA', 4, 'Avisar o morador');
INSERT INTO checklist_modelos (categoria, ordem, texto) VALUES ('HIDRAULICA', 0, 'Fechar o registro da área');
INSERT INTO checklist_modelos (categoria, ordem, texto) VALUES ('HIDRAULICA', 1, 'Localizar o vazamento ou o entupimento');
INSERT INTO checklist_modelos (categoria, ordem, texto) VALUES ('HIDRAULICA', 2, 'Consertar');
INSERT INTO checklist_modelos (categoria, ordem, texto) VALUES ('HIDRAULICA', 3, 'Testar sem vazamento');
INSERT INTO checklist_modelos (categoria, ordem, texto) VALUES ('HIDRAULICA', 4, 'Limpar o local');
INSERT INTO checklist_modelos (categoria, ordem, texto) VALUES ('ELEVADOR', 0, 'Acionar a empresa técnica');
INSERT INTO checklist_modelos (categoria, ordem, texto) VALUES ('ELEVADOR', 1, 'Sinalizar o elevador fora de serviço');
INSERT INTO checklist_modelos (categoria, ordem, texto) VALUES ('ELEVADOR', 2, 'Acompanhar o conserto');
INSERT INTO checklist_modelos (categoria, ordem, texto) VALUES ('ELEVADOR', 3, 'Testar antes de liberar');
INSERT INTO checklist_modelos (categoria, ordem, texto) VALUES ('ESTRUTURA', 0, 'Isolar e sinalizar a área');
INSERT INTO checklist_modelos (categoria, ordem, texto) VALUES ('ESTRUTURA', 1, 'Fotografar o problema');
INSERT INTO checklist_modelos (categoria, ordem, texto) VALUES ('ESTRUTURA', 2, 'Chamar o responsável técnico');
INSERT INTO checklist_modelos (categoria, ordem, texto) VALUES ('ESTRUTURA', 3, 'Registrar o laudo');
INSERT INTO checklist_modelos (categoria, ordem, texto) VALUES ('PORTARIA', 0, 'Verificar o ocorrido');
INSERT INTO checklist_modelos (categoria, ordem, texto) VALUES ('PORTARIA', 1, 'Registrar no livro da portaria');
INSERT INTO checklist_modelos (categoria, ordem, texto) VALUES ('PORTARIA', 2, 'Avisar o morador');
INSERT INTO checklist_modelos (categoria, ordem, texto) VALUES ('SEGURANCA', 0, 'Verificar o local');
INSERT INTO checklist_modelos (categoria, ordem, texto) VALUES ('SEGURANCA', 1, 'Registrar a ocorrência');
INSERT INTO checklist_modelos (categoria, ordem, texto) VALUES ('SEGURANCA', 2, 'Acionar polícia ou bombeiros, se necessário');
INSERT INTO checklist_modelos (categoria, ordem, texto) VALUES ('SEGURANCA', 3, 'Avisar o síndico');
INSERT INTO checklist_modelos (categoria, ordem, texto) VALUES ('BARULHO', 0, 'Conversar com o morador envolvido');
INSERT INTO checklist_modelos (categoria, ordem, texto) VALUES ('BARULHO', 1, 'Registrar a ocorrência');
INSERT INTO checklist_modelos (categoria, ordem, texto) VALUES ('BARULHO', 2, 'Avisar o síndico');
INSERT INTO checklist_modelos (categoria, ordem, texto) VALUES ('CONVIVENCIA', 0, 'Conversar com o morador envolvido');
INSERT INTO checklist_modelos (categoria, ordem, texto) VALUES ('CONVIVENCIA', 1, 'Registrar a ocorrência');
INSERT INTO checklist_modelos (categoria, ordem, texto) VALUES ('CONVIVENCIA', 2, 'Avisar o síndico');
INSERT INTO checklist_modelos (categoria, ordem, texto) VALUES ('ESTACIONAMENTO', 0, 'Identificar o veículo ou a vaga');
INSERT INTO checklist_modelos (categoria, ordem, texto) VALUES ('ESTACIONAMENTO', 1, 'Avisar o responsável');
INSERT INTO checklist_modelos (categoria, ordem, texto) VALUES ('ESTACIONAMENTO', 2, 'Registrar a ocorrência');
INSERT INTO checklist_modelos (categoria, ordem, texto) VALUES ('AREAS_COMUNS', 0, 'Verificar o local');
INSERT INTO checklist_modelos (categoria, ordem, texto) VALUES ('AREAS_COMUNS', 1, 'Realizar o serviço');
INSERT INTO checklist_modelos (categoria, ordem, texto) VALUES ('AREAS_COMUNS', 2, 'Conferir o resultado');
INSERT INTO checklist_modelos (categoria, ordem, texto) VALUES ('OUTROS', 0, 'Verificar o local');
INSERT INTO checklist_modelos (categoria, ordem, texto) VALUES ('OUTROS', 1, 'Realizar o serviço');
INSERT INTO checklist_modelos (categoria, ordem, texto) VALUES ('OUTROS', 2, 'Conferir o resultado');
