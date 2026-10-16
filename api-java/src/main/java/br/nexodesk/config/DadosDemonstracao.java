package br.nexodesk.config;

import br.nexodesk.dominio.Categoria;
import br.nexodesk.dominio.Perfil;
import br.nexodesk.dominio.StatusChamado;
import br.nexodesk.dominio.Usuario;
import br.nexodesk.repositorio.ChamadoRepository;
import br.nexodesk.repositorio.UsuarioRepository;
import br.nexodesk.servico.AvaliacaoService;
import br.nexodesk.servico.ChamadoService;
import br.nexodesk.web.Dtos.AlterarStatusRequest;
import br.nexodesk.web.Dtos.AvaliacaoRequest;
import br.nexodesk.web.Dtos.ChamadoResponse;
import br.nexodesk.web.Dtos.NovoChamadoForm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Popula o sistema para a demonstração. Só roda com o perfil "demo"
 * (--spring.profiles.active=demo) e só se a senha dos moradores de exemplo vier da variável de
 * ambiente NEXODESK_DEMO_SENHA; nada é criado se o banco já tiver chamados.
 */
@Component
@Profile("demo")
@Order(2) // depois do InicializadorSindico (Order 1)
public class DadosDemonstracao implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DadosDemonstracao.class);

    private final UsuarioRepository usuarios;
    private final ChamadoRepository chamados;
    private final ChamadoService servico;
    private final AvaliacaoService avaliacoes;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final PasswordEncoder codificador;
    private final String senhaDemo;
    private final String emailSindico;

    public DadosDemonstracao(UsuarioRepository usuarios, ChamadoRepository chamados, ChamadoService servico,
                             AvaliacaoService avaliacoes, org.springframework.jdbc.core.JdbcTemplate jdbc,
                             PasswordEncoder codificador,
                             @Value("${nexodesk.demo.senha:}") String senhaDemo,
                             @Value("${nexodesk.sindico.email}") String emailSindico) {
        this.usuarios = usuarios;
        this.chamados = chamados;
        this.servico = servico;
        this.avaliacoes = avaliacoes;
        this.jdbc = jdbc;
        this.codificador = codificador;
        this.senhaDemo = senhaDemo;
        this.emailSindico = emailSindico.trim().toLowerCase();
    }

    @Override
    public void run(ApplicationArguments args) {
        if (senhaDemo == null || senhaDemo.length() < 8) {
            log.warn("Perfil demo ativo, mas NEXODESK_DEMO_SENHA não foi definida (mínimo 8 caracteres): nada foi criado.");
            return;
        }
        if (chamados.count() > 0) {
            log.info("Perfil demo: o banco já tem chamados, nada foi criado.");
            return;
        }
        String ana = morador("Ana (demonstração)", "ana.demo@nexodesk.local", "Bloco A, 101");
        String bruno = morador("Bruno (demonstração)", "bruno.demo@nexodesk.local", "Bloco B, 202");

        ChamadoResponse lampada = abrir(ana, "Lâmpada queimada no corredor do 2º andar",
                Categoria.ILUMINACAO, "Corredor do 2º andar", "Precisa trocar a lâmpada do corredor, está escuro à noite.", false);
        abrir(bruno, "Elevador parado no bloco B",
                Categoria.ELEVADOR, "Bloco B", "O elevador parou entre andares e tem gente presa.", false);
        ChamadoResponse vazamento = abrir(ana, "Vazamento no teto da garagem",
                Categoria.HIDRAULICA, "Garagem, vaga 12", "Está pingando água no teto, perto das vagas 10 a 14.", false);
        abrir(ana, "Barulho depois das 22h",
                Categoria.BARULHO, "Bloco A, 3º andar", "Música alta quase toda noite, depois das 22h.", true);
        ChamadoResponse infiltracao = abrir(bruno, "Infiltração e rachadura no hall",
                Categoria.ESTRUTURA, "Hall do bloco B", "Apareceu uma rachadura na parede do hall e há infiltração junto.", false);
        ChamadoResponse lixeira = abrir(bruno, "Lixeira do térreo transbordando",
                Categoria.LIMPEZA, "Térreo", "A lixeira não é esvaziada há dias.", false);
        abrir(ana, "Portão da garagem não fecha direito",
                Categoria.SEGURANCA, "Portão da garagem", "O portão fica aberto por vários minutos depois que o carro entra.", false);
        abrir(bruno, "Reserva do salão de festas",
                Categoria.AREAS_COMUNS, "Salão de festas", "Gostaria de saber como reservar o salão para o mês que vem.", false);

        // chamados que esperam há dias, para a demonstração mostrar "subiu na fila" e "atrasado"
        envelhecer(lixeira.id(), 9);
        envelhecer(infiltracao.id(), 6);

        // dois chamados já andaram, para a demonstração mostrar histórico e avisos
        servico.alterarStatus(emailSindico, lampada.id(),
                new AlterarStatusRequest(StatusChamado.RESOLVIDO, "Lâmpada trocada hoje.", null));
        avaliacoes.avaliar(ana, lampada.id(), new AvaliacaoRequest(5, "Resolvido no mesmo dia, obrigada."));
        servico.alterarStatus(emailSindico, vazamento.id(),
                new AlterarStatusRequest(StatusChamado.EM_ANDAMENTO, "Encanador agendado para amanhã.", null));

        log.info("Perfil demo: 2 moradores (ana.demo@nexodesk.local, bruno.demo@nexodesk.local) e 8 chamados criados.");
    }

    /** Só para a demonstração: faz de conta que o chamado foi aberto há alguns dias. */
    private void envelhecer(Long id, int dias) {
        jdbc.update("UPDATE chamados SET criado_em = ? WHERE id = ?",
                java.sql.Timestamp.valueOf(java.time.LocalDateTime.now().minusDays(dias)), id);
    }

    private String morador(String nome, String email, String unidade) {
        if (!usuarios.existsByEmail(email)) {
            Usuario u = new Usuario();
            u.setNome(nome);
            u.setEmail(email);
            u.setUnidade(unidade);
            u.setSenhaHash(codificador.encode(senhaDemo));
            u.setPerfil(Perfil.MORADOR);
            usuarios.save(u);
        }
        return email;
    }

    private ChamadoResponse abrir(String email, String titulo, Categoria categoria, String local,
                                  String descricao, boolean anonimo) {
        return servico.abrir(email, new NovoChamadoForm(titulo, descricao, categoria, local, anonimo, false, null));
    }
}
