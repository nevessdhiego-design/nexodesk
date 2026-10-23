package br.nexodesk.servico;

import br.nexodesk.dominio.Categoria;
import java.util.List;

/** Etapas padrão de cada tipo de serviço. É um modelo: o funcionário só marca o que foi feito, e a conclusão não depende dele. */
public final class ChecklistModelo {

    private ChecklistModelo() {
    }

    public static List<String> itens(Categoria categoria) {
        return switch (categoria) {
            case LIMPEZA -> List.of("Recolher o lixo e os resíduos", "Varrer ou lavar o local", "Secar o piso para evitar escorregões", "Conferir se ficou limpo");
            case ILUMINACAO -> List.of("Conferir qual lâmpada ou luminária está com defeito", "Trocar a lâmpada ou o reator", "Testar o funcionamento");
            case ELETRICA -> List.of("Desligar o disjuntor do trecho", "Localizar o defeito", "Consertar ou trocar o componente", "Religar e testar", "Avisar o morador");
            case HIDRAULICA -> List.of("Fechar o registro da área", "Localizar o vazamento ou o entupimento", "Consertar", "Testar sem vazamento", "Limpar o local");
            case ELEVADOR -> List.of("Acionar a empresa técnica", "Sinalizar o elevador fora de serviço", "Acompanhar o conserto", "Testar antes de liberar");
            case ESTRUTURA -> List.of("Isolar e sinalizar a área", "Fotografar o problema", "Chamar o responsável técnico", "Registrar o laudo");
            case PORTARIA -> List.of("Verificar o ocorrido", "Registrar no livro da portaria", "Avisar o morador");
            case SEGURANCA -> List.of("Verificar o local", "Registrar a ocorrência", "Acionar polícia ou bombeiros, se necessário", "Avisar o síndico");
            case BARULHO, CONVIVENCIA -> List.of("Conversar com o morador envolvido", "Registrar a ocorrência", "Avisar o síndico");
            case ESTACIONAMENTO -> List.of("Identificar o veículo ou a vaga", "Avisar o responsável", "Registrar a ocorrência");
            case AREAS_COMUNS, OUTROS -> List.of("Verificar o local", "Realizar o serviço", "Conferir o resultado");
        };
    }
}
