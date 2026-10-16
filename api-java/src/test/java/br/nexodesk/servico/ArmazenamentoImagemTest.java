package br.nexodesk.servico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

/** A foto que o morador manda não pode levar a localização dele (EXIF/GPS) para o servidor. */
class ArmazenamentoImagemTest {

    static final String MARCADOR_GPS = "GPS-LAT-23.5505S-LON-46.6333W-MARCADOR-SECRETO";

    @TempDir
    Path pasta;

    // ---------- fabricação de imagens de teste ----------

    static BufferedImage imagem(int largura, int altura) {
        BufferedImage i = new BufferedImage(largura, altura, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = i.createGraphics();
        g.setColor(Color.ORANGE);
        g.fillRect(0, 0, largura, altura);
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, largura / 4, altura / 4); // marca no canto superior esquerdo
        g.dispose();
        return i;
    }

    static byte[] codificar(BufferedImage img, String formato) throws IOException {
        ByteArrayOutputStream s = new ByteArrayOutputStream();
        ImageIO.write(img, formato, s);
        return s.toByteArray();
    }

    /** JPEG com um bloco EXIF (orientação + texto de "GPS") e um comentário com o mesmo texto, como numa foto de celular. */
    static byte[] jpegComExif(int largura, int altura, int orientacao) throws IOException {
        byte[] jpg = codificar(imagem(largura, altura), "jpg");
        byte[] marcador = MARCADOR_GPS.getBytes(StandardCharsets.US_ASCII);

        ByteArrayOutputStream tiff = new ByteArrayOutputStream();
        tiff.write(new byte[]{'I', 'I', 0x2A, 0x00, 0x08, 0x00, 0x00, 0x00}); // cabeçalho, IFD0 no byte 8
        tiff.write(new byte[]{0x01, 0x00});                                   // 1 entrada
        tiff.write(new byte[]{0x12, 0x01, 0x03, 0x00, 0x01, 0x00, 0x00, 0x00, (byte) orientacao, 0x00, 0x00, 0x00});
        tiff.write(new byte[]{0x00, 0x00, 0x00, 0x00});                       // sem próximo IFD
        tiff.write(marcador);                                                 // "GPS" solto no bloco EXIF

        ByteArrayOutputStream app1 = new ByteArrayOutputStream();
        int tamanho = 2 + 6 + tiff.size();
        app1.write(new byte[]{(byte) 0xFF, (byte) 0xE1, (byte) (tamanho >> 8), (byte) tamanho});
        app1.write(new byte[]{'E', 'x', 'i', 'f', 0, 0});
        app1.write(tiff.toByteArray());

        ByteArrayOutputStream com = new ByteArrayOutputStream();
        int tamanhoCom = 2 + marcador.length;
        com.write(new byte[]{(byte) 0xFF, (byte) 0xFE, (byte) (tamanhoCom >> 8), (byte) tamanhoCom});
        com.write(marcador);

        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        saida.write(jpg, 0, 2);                    // SOI
        saida.write(app1.toByteArray());
        saida.write(com.toByteArray());
        saida.write(jpg, 2, jpg.length - 2);
        return saida.toByteArray();
    }

    ArmazenamentoImagem armazenamento() throws IOException {
        return new ArmazenamentoImagem(pasta.toString());
    }

    BufferedImage lerSalvo(String nome) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(Files.readAllBytes(pasta.resolve(nome))));
    }

    // ---------- testes ----------

    @Test
    void aFotoDeEntradaDeVerdadeTemOGpsEOExif() throws IOException {
        // garante que o teste não é "vazio": o arquivo de entrada realmente carrega os metadados
        byte[] entrada = jpegComExif(40, 20, 1);
        String texto = new String(entrada, StandardCharsets.ISO_8859_1);
        assertThat(texto).contains("Exif").contains(MARCADOR_GPS);
        assertThat(ProcessadorImagem.lerOrientacaoExif(jpegComExif(40, 20, 6))).isEqualTo(6);
    }

    @Test
    void salvarRemoveExifGpsEComentarioDoJpeg() throws IOException {
        ArmazenamentoImagem a = armazenamento();
        String nome = a.salvar(new MockMultipartFile("imagem", "foto.jpg", "image/jpeg", jpegComExif(40, 20, 1)));

        byte[] salvo = Files.readAllBytes(pasta.resolve(nome));
        String texto = new String(salvo, StandardCharsets.ISO_8859_1);
        assertThat(texto).doesNotContain("Exif").doesNotContain("GPS").doesNotContain("MARCADOR");
        assertThat(nome).endsWith(".jpg");
        assertThat(lerSalvo(nome)).isNotNull(); // continua sendo um JPEG válido
        assertThat(lerSalvo(nome).getWidth()).isEqualTo(40);
    }

    @Test
    void orientacaoDoCelularEAplicadaAntesDeRemoverOExif() throws IOException {
        ArmazenamentoImagem a = armazenamento();
        // 40x20 com orientação 6 (girar 90 graus): a foto "de pé" deve sair 20x40
        String girada = a.salvar(new MockMultipartFile("imagem", "f.jpg", "image/jpeg", jpegComExif(40, 20, 6)));
        assertThat(lerSalvo(girada).getWidth()).isEqualTo(20);
        assertThat(lerSalvo(girada).getHeight()).isEqualTo(40);

        // orientação 3 (180 graus) mantém as dimensões
        String virada = a.salvar(new MockMultipartFile("imagem", "f.jpg", "image/jpeg", jpegComExif(40, 20, 3)));
        assertThat(lerSalvo(virada).getWidth()).isEqualTo(40);
    }

    @Test
    void orientacaoMudaOsPixelsDeVerdade() {
        // marca azul no canto superior esquerdo; girar 90 graus horário leva o canto para o superior DIREITO
        BufferedImage original = imagem(40, 20);
        BufferedImage girada = ProcessadorImagem.orientar(original, 6);
        int azul = Color.BLUE.getRGB();
        assertThat(girada.getRGB(girada.getWidth() - 1, 0)).isEqualTo(azul);
        assertThat(girada.getRGB(0, 0)).isNotEqualTo(azul);
    }

    @Test
    void fotoGrandeEReduzidaParaNoMaximo2048() throws IOException {
        ArmazenamentoImagem a = armazenamento();
        String nome = a.salvar(new MockMultipartFile("imagem", "grande.jpg", "image/jpeg",
                codificar(imagem(3000, 1500), "jpg")));
        BufferedImage salva = lerSalvo(nome);
        assertThat(salva.getWidth()).isEqualTo(ProcessadorImagem.LADO_MAXIMO);
        assertThat(salva.getHeight()).isEqualTo(1024);
    }

    @Test
    void pngETambemRecodificadoESegueValido() throws IOException {
        ArmazenamentoImagem a = armazenamento();
        String nome = a.salvar(new MockMultipartFile("imagem", "f.png", "image/png", codificar(imagem(30, 30), "png")));
        assertThat(nome).endsWith(".png");
        assertThat(lerSalvo(nome).getWidth()).isEqualTo(30);
    }

    @Test
    void gifPassaSemMudar() throws IOException {
        byte[] gif = codificar(imagem(10, 10), "gif");
        ArmazenamentoImagem a = armazenamento();
        String nome = a.salvar(new MockMultipartFile("imagem", "f.gif", "image/gif", gif));
        assertThat(Files.readAllBytes(pasta.resolve(nome))).isEqualTo(gif);
    }

    @Test
    void imagemBombaComMilhoesDePixelsERecusadaAntesDeDecodificar() throws IOException {
        // 7000x7000 = 49 milhões de pixels, quase vazia (poucos KB), mas viraria ~190 MB na memória
        byte[] bomba = codificar(new BufferedImage(7000, 7000, BufferedImage.TYPE_BYTE_GRAY), "png");
        assertThat(bomba.length).isLessThan(5 * 1024 * 1024);
        ArmazenamentoImagem a = armazenamento();
        assertThatThrownBy(() -> a.salvar(new MockMultipartFile("imagem", "b.png", "image/png", bomba)))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getMessage()).contains("resolução grande demais");
                });
        try (var arquivos = Files.list(pasta)) {
            assertThat(arquivos.count()).as("nada pode ter sido gravado").isZero();
        }
    }

    @Test
    void jpegCorrompidoVira400ENaoErro500() throws IOException {
        byte[] quebrado = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x01, 0x02, 0x03, 0x04, 0x05};
        ArmazenamentoImagem a = armazenamento();
        assertThatThrownBy(() -> a.salvar(new MockMultipartFile("imagem", "q.jpg", "image/jpeg", quebrado)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void removerApagaSoArquivoComNomeValidoDaPasta() throws IOException {
        ArmazenamentoImagem a = armazenamento();
        String nome = a.salvar(new MockMultipartFile("imagem", "f.png", "image/png", codificar(imagem(8, 8), "png")));
        Path fora = pasta.getParent().resolve("nao-pode-apagar.txt");
        Files.writeString(fora, "x");
        try {
            a.remover("../nao-pode-apagar.txt"); // nome inválido: ignorado
            a.remover(null);
            assertThat(fora).exists();
            assertThat(pasta.resolve(nome)).exists();
            a.remover(nome);
            assertThat(pasta.resolve(nome)).doesNotExist();
        } finally {
            Files.deleteIfExists(fora);
        }
    }
}
