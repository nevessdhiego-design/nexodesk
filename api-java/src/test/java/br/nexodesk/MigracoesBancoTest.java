package br.nexodesk;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/**
 * Garante que o banco evolui sem perder dados. O cenário que mais importa: o banco que o grupo já tem foi criado
 * pelo antigo schema.sql, SEM Flyway. Ele precisa entrar no Flyway (baseline na versão 1) e receber as migrações
 * novas com os dados intactos.
 */
class MigracoesBancoTest {

    static final Path PASTA = Path.of("src", "main", "resources", "db", "migration");

    /**
     * SHA-256 do V1 (com quebras de linha normalizadas). O V1 NUNCA pode mudar depois de publicado: o Flyway
     * guarda o checksum e todo banco já criado passaria a recusar a subida. Mudança de esquema = arquivo novo.
     */
    static final String SHA256_DO_V1 = "68f29b078d351830fcdf054c968e29bbeebd5d5afa036f147199b2bb531d3dbb";

    static JdbcDataSource banco() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:migr-" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        return ds;
    }

    static Flyway flyway(JdbcDataSource ds) {
        return Flyway.configure().dataSource(ds)
                .locations("filesystem:" + PASTA.toAbsolutePath())
                .baselineOnMigrate(true).baselineVersion("1")
                .load();
    }

    static String sha256DoV1() throws Exception {
        String conteudo = Files.readString(PASTA.resolve("V1__base.sql"), StandardCharsets.UTF_8).replace("\r\n", "\n");
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(conteudo.getBytes(StandardCharsets.UTF_8)));
    }

    static int ultimaVersao() throws Exception {
        Pattern p = Pattern.compile("V(\\d+)__.+\\.sql");
        try (Stream<Path> arquivos = Files.list(PASTA)) {
            return arquivos.map(a -> p.matcher(a.getFileName().toString()))
                    .filter(Matcher::matches).mapToInt(m -> Integer.parseInt(m.group(1))).max().orElseThrow();
        }
    }

    @Test
    void v1NuncaPodeMudarDepoisDePublicado() throws Exception {
        assertThat(sha256DoV1())
                .as("O V1 foi alterado! Isso quebra todo banco já criado. Desfaça a edição e crie um V<n+1>__*.sql com a mudança.")
                .isEqualTo(SHA256_DO_V1);
    }

    @Test
    void arquivosDeMigracaoSaoSequenciaisSemBuracos() throws Exception {
        try (Stream<Path> arquivos = Files.list(PASTA)) {
            List<Integer> versoes = arquivos.map(a -> a.getFileName().toString())
                    .filter(n -> n.matches("V\\d+__.+\\.sql"))
                    .map(n -> Integer.parseInt(n.substring(1, n.indexOf("__")))).sorted().toList();
            for (int i = 0; i < versoes.size(); i++) {
                assertThat(versoes.get(i)).as("migração na posição %d", i + 1).isEqualTo(i + 1);
            }
        }
    }

    @Test
    void bancoNovoChegaNaUltimaVersaoEPassaNaValidacao() throws Exception {
        JdbcDataSource ds = banco();
        Flyway f = flyway(ds);
        f.migrate();
        f.validate();
        assertThat(f.info().current().getVersion().getVersion()).isEqualTo(String.valueOf(ultimaVersao()));
        assertThat(f.info().pending()).isEmpty();
    }

    @Test
    void bancoCriadoAntesDoFlywayMigraSemPerderDados() throws Exception {
        JdbcDataSource ds = banco();
        // 1) o "banco legado": exatamente o que o antigo schema.sql criava (= V1), com dados reais dentro
        String v1 = Files.readString(PASTA.resolve("V1__base.sql"), StandardCharsets.UTF_8);
        try (Connection c = ds.getConnection()) {
            ScriptUtils.executeSqlScript(c, new ByteArrayResource(v1.getBytes(StandardCharsets.UTF_8)));
            try (Statement s = c.createStatement()) {
                s.execute("INSERT INTO usuarios (nome, email, senha_hash, perfil) VALUES ('Legado', 'legado@x.test', '$2a$10$abc', 'MORADOR')");
                s.execute("INSERT INTO chamados (titulo, descricao, categoria, status, dificuldade, pontuacao, morador_id) "
                        + "VALUES ('Chamado antigo', 'criado antes do Flyway', 'OUTROS', 'ABERTO', 'MEDIA', 4, 1)");
            }
        }
        Flyway f = flyway(ds);
        assertThat(f.info().current()).as("ainda sem histórico do Flyway").isNull();

        // 2) migra: entra com baseline na versão 1 e recebe só as migrações novas
        f.migrate();
        f.validate();

        MigrationInfo[] historico = f.info().applied();
        assertThat(historico[0].getVersion().getVersion()).isEqualTo("1");
        assertThat(historico[0].getType().name()).isEqualTo("BASELINE");
        assertThat(f.info().current().getVersion().getVersion()).isEqualTo(String.valueOf(ultimaVersao()));

        // 3) os dados continuam lá, e as colunas novas existem com valor padrão
        try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
            ResultSet r = s.executeQuery("SELECT titulo, versao FROM chamados WHERE morador_id = 1");
            assertThat(r.next()).isTrue();
            assertThat(r.getString("titulo")).isEqualTo("Chamado antigo");
            assertThat(r.getLong("versao")).isZero();
            r = s.executeQuery("SELECT email, versao_sessao FROM usuarios WHERE email = 'legado@x.test'");
            assertThat(r.next()).isTrue();
            assertThat(r.getInt("versao_sessao")).isZero();
        }
    }
}
