package br.nexodesk.servico;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import org.springframework.http.HttpStatus;

/**
 * Limpa a foto antes de guardar. Foto de celular carrega metadados (EXIF) com a <b>localização GPS</b>, o modelo do
 * aparelho e a data, e nada disso deve ir para o servidor nem para o síndico. A imagem é decodificada e gravada de
 * novo SEM metadados. Antes disso aplicamos a rotação que o EXIF pedia, senão a foto de pé apareceria deitada.
 *
 * <ul>
 *   <li>JPEG e PNG: decodifica, endireita, reduz (lado maior até 2048 px) e grava sem metadados;</li>
 *   <li>GIF: guardado como veio (não tem GPS, e recodificar apagaria a animação);</li>
 *   <li>recusa imagem com mais de 40 milhões de pixels ("imagem-bomba": poucos KB que viram gigabytes na memória).</li>
 * </ul>
 */
final class ProcessadorImagem {

    static final int LADO_MAXIMO = 2048;
    static final long PIXELS_MAXIMOS = 40_000_000L;
    private static final float QUALIDADE_JPEG = 0.88f;

    private ProcessadorImagem() {
    }

    static byte[] limpar(byte[] original, String extensao) {
        if ("gif".equals(extensao)) {
            return original;
        }
        try (ImageInputStream entrada = ImageIO.createImageInputStream(new ByteArrayInputStream(original))) {
            Iterator<ImageReader> leitores = ImageIO.getImageReaders(entrada);
            if (!leitores.hasNext()) {
                throw invalida();
            }
            ImageReader leitor = leitores.next();
            BufferedImage imagem;
            try {
                leitor.setInput(entrada, true, true);
                long pixels = (long) leitor.getWidth(0) * leitor.getHeight(0);
                if (pixels > PIXELS_MAXIMOS) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "A imagem tem resolução grande demais.");
                }
                imagem = leitor.read(0);
            } finally {
                leitor.dispose();
            }
            boolean jpeg = "jpg".equals(extensao);
            if (jpeg) {
                imagem = orientar(imagem, lerOrientacaoExif(original));
            }
            imagem = reduzir(imagem);
            return jpeg ? gravarJpeg(imagem) : gravarPng(imagem);
        } catch (ApiException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw invalida();
        }
    }

    private static ApiException invalida() {
        return new ApiException(HttpStatus.BAD_REQUEST, "Não foi possível processar a imagem. Envie outra foto.");
    }

    // ---------- EXIF: só a orientação (1 a 8) ----------

    /** Lê a tag de orientação do EXIF de um JPEG. Devolve 1 (normal) se não houver ou se algo estiver estranho. */
    static int lerOrientacaoExif(byte[] b) {
        try {
            if (b.length < 4 || (b[0] & 0xFF) != 0xFF || (b[1] & 0xFF) != 0xD8) {
                return 1;
            }
            int pos = 2;
            while (pos + 4 <= b.length) {
                if ((b[pos] & 0xFF) != 0xFF) {
                    return 1;
                }
                int marcador = b[pos + 1] & 0xFF;
                if (marcador == 0xDA || marcador == 0xD9) { // início dos dados da imagem: acabaram os metadados
                    return 1;
                }
                int tamanho = ((b[pos + 2] & 0xFF) << 8) | (b[pos + 3] & 0xFF);
                if (tamanho < 2) {
                    return 1;
                }
                if (marcador == 0xE1 && tamanho >= 14 && b[pos + 4] == 'E' && b[pos + 5] == 'x' && b[pos + 6] == 'i'
                        && b[pos + 7] == 'f' && b[pos + 8] == 0 && b[pos + 9] == 0) {
                    return orientacaoDoTiff(b, pos + 10, pos + 2 + tamanho);
                }
                pos += 2 + tamanho;
            }
        } catch (RuntimeException e) {
            // índice fora do arquivo ou estrutura corrompida: segue sem girar
        }
        return 1;
    }

    private static int orientacaoDoTiff(byte[] b, int tiff, int fim) {
        boolean little = b[tiff] == 'I' && b[tiff + 1] == 'I';
        boolean big = b[tiff] == 'M' && b[tiff + 1] == 'M';
        if (!little && !big) {
            return 1;
        }
        int ifd = tiff + (int) inteiro(b, tiff + 4, 4, little);
        int entradas = (int) inteiro(b, ifd, 2, little);
        for (int i = 0; i < entradas; i++) {
            int e = ifd + 2 + i * 12;
            if (e + 12 > fim) {
                return 1;
            }
            if (inteiro(b, e, 2, little) == 0x0112) {
                int v = (int) inteiro(b, e + 8, 2, little);
                return v >= 1 && v <= 8 ? v : 1;
            }
        }
        return 1;
    }

    private static long inteiro(byte[] b, int pos, int bytes, boolean little) {
        long v = 0;
        for (int i = 0; i < bytes; i++) {
            int byteAtual = b[pos + (little ? bytes - 1 - i : i)] & 0xFF;
            v = (v << 8) | byteAtual;
        }
        return v;
    }

    // ---------- transformações ----------

    static BufferedImage orientar(BufferedImage origem, int orientacao) {
        int w = origem.getWidth();
        int h = origem.getHeight();
        AffineTransform t = switch (orientacao) {
            case 2 -> new AffineTransform(-1, 0, 0, 1, w, 0);   // espelho horizontal
            case 3 -> new AffineTransform(-1, 0, 0, -1, w, h);  // 180 graus
            case 4 -> new AffineTransform(1, 0, 0, -1, 0, h);   // espelho vertical
            case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0);    // transposta
            case 6 -> new AffineTransform(0, 1, -1, 0, h, 0);   // 90 graus horário
            case 7 -> new AffineTransform(0, -1, -1, 0, h, w);  // transversa
            case 8 -> new AffineTransform(0, -1, 1, 0, 0, w);   // 90 graus anti-horário
            default -> null;
        };
        if (t == null) {
            return origem;
        }
        boolean troca = orientacao >= 5;
        BufferedImage destino = new BufferedImage(troca ? h : w, troca ? w : h, tipo(origem));
        Graphics2D g = destino.createGraphics();
        g.drawImage(origem, t, null);
        g.dispose();
        return destino;
    }

    static BufferedImage reduzir(BufferedImage origem) {
        int maior = Math.max(origem.getWidth(), origem.getHeight());
        if (maior <= LADO_MAXIMO) {
            return origem;
        }
        double escala = (double) LADO_MAXIMO / maior;
        int w = Math.max(1, (int) Math.round(origem.getWidth() * escala));
        int h = Math.max(1, (int) Math.round(origem.getHeight() * escala));
        BufferedImage destino = new BufferedImage(w, h, tipo(origem));
        Graphics2D g = destino.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(origem, 0, 0, w, h, null);
        g.dispose();
        return destino;
    }

    private static int tipo(BufferedImage i) {
        return i.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
    }

    // ---------- gravação sem metadados ----------

    private static byte[] gravarJpeg(BufferedImage imagem) throws IOException {
        BufferedImage rgb = imagem;
        if (imagem.getColorModel().hasAlpha()) { // JPEG não tem transparência: fundo branco
            rgb = new BufferedImage(imagem.getWidth(), imagem.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = rgb.createGraphics();
            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
            g.drawImage(imagem, 0, 0, null);
            g.dispose();
        }
        ImageWriter escritor = ImageIO.getImageWritersByFormatName("jpeg").next();
        try (ByteArrayOutputStream saida = new ByteArrayOutputStream();
             MemoryCacheImageOutputStream fluxo = new MemoryCacheImageOutputStream(saida)) {
            ImageWriteParam parametros = escritor.getDefaultWriteParam();
            parametros.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            parametros.setCompressionQuality(QUALIDADE_JPEG);
            escritor.setOutput(fluxo);
            // metadata = null: nenhum EXIF, comentário nem miniatura é escrito
            escritor.write(null, new IIOImage(rgb, null, null), parametros);
            fluxo.flush();
            return saida.toByteArray();
        } finally {
            escritor.dispose();
        }
    }

    private static byte[] gravarPng(BufferedImage imagem) throws IOException {
        try (ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
            if (!ImageIO.write(imagem, "png", saida)) {
                throw invalida();
            }
            return saida.toByteArray();
        }
    }
}
