package br.com.desafio.activation.api;

import br.com.desafio.activation.service.ActivationRequestService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> validation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        ProblemDetail p = problem(HttpStatus.BAD_REQUEST, "Dados inválidos", "Um ou mais campos não passaram na validação.", req);
        p.setProperty("errors", ex.getBindingResult().getFieldErrors().stream().map(e -> Map.of("field", e.getField(), "message", e.getDefaultMessage() == null ? "inválido" : e.getDefaultMessage())).toList());
        return ResponseEntity.badRequest().body(p);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> unreadableBody(HttpMessageNotReadableException ex, HttpServletRequest req) {
        return ResponseEntity.badRequest().body(problem(HttpStatus.BAD_REQUEST, "Dados inválidos", "O corpo da requisição está ausente ou não é um JSON válido.", req));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ProblemDetail> typeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
        return ResponseEntity.badRequest().body(problem(HttpStatus.BAD_REQUEST, "Dados inválidos", "Valor inválido para o parâmetro '" + ex.getName() + "'.", req));
    }

    @ExceptionHandler(ActivationRequestService.DuplicateExternalIdException.class)
    ResponseEntity<ProblemDetail> duplicate(RuntimeException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem(HttpStatus.CONFLICT, "Solicitação duplicada", ex.getMessage(), req));
    }

    @ExceptionHandler(ActivationRequestService.ActivationRequestNotFoundException.class)
    ResponseEntity<ProblemDetail> notFound(RuntimeException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem(HttpStatus.NOT_FOUND, "Não encontrado", ex.getMessage(), req));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(Exception ex, HttpServletRequest req) {
        if (ex instanceof ErrorResponse errorResponse) {
            HttpStatusCode status = errorResponse.getStatusCode();
            HttpStatus resolved = HttpStatus.resolve(status.value());
            String title = resolved == null ? "Erro na requisição" : resolved.getReasonPhrase();
            return ResponseEntity.status(status).body(problem(status, title, errorResponse.getBody().getDetail(), req));
        }
        ProblemDetail p = problem(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno", "Ocorreu um erro inesperado. Consulte os logs usando o traceId.", req);
        log.error("Erro inesperado em {} traceId={}", req.getRequestURI(), p.getProperties().get("traceId"), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(p);
    }

    private ProblemDetail problem(HttpStatusCode status, String title, String detail, HttpServletRequest req) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(status, detail);
        p.setTitle(title);
        p.setType(URI.create("https://example.invalid/problems/" + status.value()));
        p.setInstance(URI.create(req.getRequestURI()));
        p.setProperty("traceId", UUID.randomUUID().toString());
        return p;
    }
}
