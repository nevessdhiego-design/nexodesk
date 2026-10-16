package br.nexodesk.web;

import br.nexodesk.servico.ApiException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/** Todas as falhas viram JSON {"erro": "...", "campos": {...}}; nunca vaza stack trace. */
@RestControllerAdvice
public class TratadorErros {

    private static final Logger log = LoggerFactory.getLogger(TratadorErros.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<Map<String, Object>> aoFalharRegra(ApiException e) {
        return ResponseEntity.status(e.getStatus()).body(Map.of("erro", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> aoValidarCorpo(MethodArgumentNotValidException e) {
        return invalido(e.getBindingResult().getFieldErrors());
    }

    @ExceptionHandler(BindException.class)
    ResponseEntity<Map<String, Object>> aoValidarFormulario(BindException e) {
        return invalido(e.getBindingResult().getFieldErrors());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Map<String, Object>> aoLerCorpo(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(Map.of("erro", "Corpo da requisição inválido."));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<Map<String, Object>> aoExcederUpload(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE).body(Map.of("erro", "A imagem deve ter no máximo 5 MB."));
    }

    /** Duas gravações simultâneas no mesmo chamado: a segunda perde e recebe um aviso claro. */
    @ExceptionHandler(org.springframework.orm.ObjectOptimisticLockingFailureException.class)
    ResponseEntity<Map<String, Object>> aoConflitarGravacao(org.springframework.orm.ObjectOptimisticLockingFailureException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("erro",
                "Este chamado foi alterado por outra pessoa. Atualize a lista e tente de novo."));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, Object>> aoFalharInesperado(Exception e) throws Exception {
        // deixa o Spring Security tratar 401/403 e o MVC tratar 404/405
        if (e instanceof org.springframework.security.access.AccessDeniedException
                || e instanceof org.springframework.security.core.AuthenticationException
                || e instanceof org.springframework.web.ErrorResponse) {
            throw e;
        }
        log.error("Erro inesperado", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("erro", "Erro interno. Tente novamente."));
    }

    private static ResponseEntity<Map<String, Object>> invalido(java.util.List<FieldError> erros) {
        Map<String, String> campos = new LinkedHashMap<>();
        for (FieldError f : erros) {
            // erro de conversão (ex.: categoria que não existe) traz texto interno do Spring: não expor
            String mensagem = f.isBindingFailure() || f.getDefaultMessage() == null
                    ? "Valor inválido para o campo '" + f.getField() + "'."
                    : f.getDefaultMessage();
            campos.putIfAbsent(f.getField(), mensagem);
        }
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("erro", campos.isEmpty() ? "Dados inválidos." : campos.values().iterator().next());
        corpo.put("campos", campos);
        return ResponseEntity.badRequest().body(corpo);
    }
}
