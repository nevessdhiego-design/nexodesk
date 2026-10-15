package br.nexodesk.web;

import br.nexodesk.dominio.Categoria;
import br.nexodesk.servico.ChamadoService;
import br.nexodesk.web.Dtos.CategoriaResponse;
import br.nexodesk.web.Dtos.ChamadoResponse;
import br.nexodesk.web.Dtos.NovoChamadoForm;
import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ChamadoController {

    private final ChamadoService chamados;

    public ChamadoController(ChamadoService chamados) {
        this.chamados = chamados;
    }

    @GetMapping("/categorias")
    public List<CategoriaResponse> categorias() {
        return Arrays.stream(Categoria.values())
                .map(c -> new CategoriaResponse(c.name(), c.getRotulo()))
                .toList();
    }

    @PostMapping(value = "/chamados", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ChamadoResponse abrir(@Valid @ModelAttribute NovoChamadoForm form, Authentication autenticacao) {
        return chamados.abrir(autenticacao.getName(), form);
    }

    @GetMapping("/chamados/meus")
    public List<ChamadoResponse> meus(Authentication autenticacao) {
        return chamados.meus(autenticacao.getName());
    }

    @GetMapping("/chamados/{id}")
    public ChamadoResponse detalhe(@PathVariable Long id, Authentication autenticacao) {
        return chamados.detalhe(autenticacao.getName(), id);
    }

    @GetMapping("/chamados/{id}/imagem")
    public ResponseEntity<Resource> imagem(@PathVariable Long id, Authentication autenticacao) {
        Resource recurso = chamados.imagem(autenticacao.getName(), id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(chamados.tipoImagem(recurso)))
                .header("Cache-Control", "private, max-age=3600")
                .body(recurso);
    }
}
