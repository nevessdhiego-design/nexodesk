package br.nexodesk.servico;

import br.nexodesk.dominio.Categoria;
import br.nexodesk.dominio.Funcao;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Configuração que o síndico edita: quais categorias cada função cobre (é o que o painel usa para marcar "Indicado") e os
 * modelos de checklist de cada categoria. O padrão de fábrica vem da migração V11 e do código ({@link Funcao},
 * {@link ChecklistModelo}); "restaurar padrão" volta a ele.
 */
@Service
public class ConfiguracaoService {

    static final int MAXIMO_ITENS = 12;
    static final int MAXIMO_TEXTO = 120;

    private final JdbcTemplate jdbc;

    public ConfiguracaoService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Map<Funcao, Set<Categoria>> matriz() {
        Map<Funcao, Set<Categoria>> m = new EnumMap<>(Funcao.class);
        for (Funcao f : Funcao.values()) {
            m.put(f, EnumSet.noneOf(Categoria.class));
        }
        jdbc.query("SELECT funcao, categoria FROM funcao_categorias", rs -> {
            try {
                m.get(Funcao.valueOf(rs.getString("funcao"))).add(Categoria.valueOf(rs.getString("categoria")));
            } catch (IllegalArgumentException ignorado) {
                // linha de uma função ou categoria que não existe mais: ignora
            }
        });
        return m;
    }

    @Transactional(readOnly = true)
    public List<String> checklist(Categoria categoria) {
        List<String> itens = jdbc.queryForList("SELECT texto FROM checklist_modelos WHERE categoria = ? ORDER BY ordem, id",
                String.class, categoria.name());
        return itens.isEmpty() ? ChecklistModelo.itens(categoria) : itens;
    }

    /** Substitui a matriz inteira. Toda categoria precisa continuar com pelo menos uma função que a cubra. */
    @Transactional
    public void salvarMatriz(Map<Funcao, ? extends Iterable<Categoria>> nova) {
        Map<Funcao, Set<Categoria>> m = new EnumMap<>(Funcao.class);
        for (Funcao f : Funcao.values()) {
            m.put(f, EnumSet.noneOf(Categoria.class));
        }
        nova.forEach((f, cats) -> {
            if (f != null && cats != null) {
                cats.forEach(c -> {
                    if (c != null) {
                        m.get(f).add(c);
                    }
                });
            }
        });
        for (Categoria c : Categoria.values()) {
            if (m.values().stream().noneMatch(s -> s.contains(c))) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "A categoria " + c.getRotulo() + " ficaria sem nenhuma função que a cubra. Marque pelo menos uma.");
            }
        }
        jdbc.update("DELETE FROM funcao_categorias");
        m.forEach((f, cats) -> cats.forEach(c ->
                jdbc.update("INSERT INTO funcao_categorias (funcao, categoria) VALUES (?, ?)", f.name(), c.name())));
    }

    @Transactional
    public void salvarChecklist(Categoria categoria, List<String> itens) {
        List<String> limpos = new ArrayList<>();
        for (String t : itens == null ? List.<String>of() : itens) {
            String texto = t == null ? "" : t.trim();
            if (!texto.isEmpty()) {
                if (texto.length() > MAXIMO_TEXTO) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "Cada etapa deve ter no máximo " + MAXIMO_TEXTO + " caracteres.");
                }
                limpos.add(texto);
            }
        }
        if (limpos.isEmpty() || limpos.size() > MAXIMO_ITENS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "O checklist deve ter de 1 a " + MAXIMO_ITENS + " etapas.");
        }
        jdbc.update("DELETE FROM checklist_modelos WHERE categoria = ?", categoria.name());
        int ordem = 0;
        for (String texto : limpos) {
            jdbc.update("INSERT INTO checklist_modelos (categoria, ordem, texto) VALUES (?, ?, ?)", categoria.name(), ordem++, texto);
        }
    }

    /** Volta tudo ao padrão de fábrica (o definido no código). */
    @Transactional
    public void restaurarPadrao() {
        Map<Funcao, Set<Categoria>> padrao = new EnumMap<>(Funcao.class);
        for (Funcao f : Funcao.values()) {
            padrao.put(f, f.getCategorias());
        }
        salvarMatriz(padrao);
        for (Categoria c : Categoria.values()) {
            salvarChecklist(c, ChecklistModelo.itens(c));
        }
    }
}
