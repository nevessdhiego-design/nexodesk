package br.nexodesk.servico;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Guarda as fotos dos chamados fora da pasta pública. O tipo é decidido pelo CONTEÚDO do
 * arquivo (assinatura dos primeiros bytes), não pela extensão nem pelo Content-Type
 * enviado pelo navegador, e o nome em disco é gerado pelo sistema.
 */
@Service
public class ArmazenamentoImagem {

    private static final Pattern NOME_VALIDO = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png|gif)");

    private final Path raiz;

    public ArmazenamentoImagem(@Value("${nexodesk.uploads.dir}") String pasta) throws IOException {
        this.raiz = Path.of(pasta).toAbsolutePath().normalize();
        Files.createDirectories(raiz);
    }

    /** Salva e devolve o nome gerado, ou null se nenhuma imagem foi enviada. */
    public String salvar(MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) {
            return null;
        }
        try (InputStream in = arquivo.getInputStream()) {
            byte[] cabecalho = in.readNBytes(12);
            String extensao = detectarExtensao(cabecalho);
            if (extensao == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Envie uma imagem JPG, PNG ou GIF válida.");
            }
            String nome = UUID.randomUUID() + "." + extensao;
            try (InputStream completo = arquivo.getInputStream()) {
                Files.copy(completo, raiz.resolve(nome));
            }
            return nome;
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Não foi possível salvar a imagem.");
        }
    }

    public Resource carregar(String nome) {
        if (nome == null || !NOME_VALIDO.matcher(nome).matches()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Imagem não encontrada.");
        }
        Path caminho = raiz.resolve(nome).normalize();
        if (!caminho.startsWith(raiz) || !Files.isRegularFile(caminho)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Imagem não encontrada.");
        }
        return new PathResource(caminho);
    }

    public MediaType tipoDe(String nome) {
        if (nome.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }
        if (nome.endsWith(".gif")) {
            return MediaType.IMAGE_GIF;
        }
        return MediaType.IMAGE_JPEG;
    }

    static String detectarExtensao(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) {
            return "png";
        }
        if (b.length >= 6 && b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8'
                && (b[4] == '7' || b[4] == '9') && b[5] == 'a') {
            return "gif";
        }
        return null;
    }
}
