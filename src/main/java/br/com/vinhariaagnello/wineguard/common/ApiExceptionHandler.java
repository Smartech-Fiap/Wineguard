package br.com.vinhariaagnello.wineguard.common;

import java.time.Instant;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;

/** Converte falhas conhecidas em respostas HTTP curtas e consistentes. */
@RestControllerAdvice
public class ApiExceptionHandler {

    /** Responde 404 quando um ambiente ou dispositivo solicitado não existe. */
    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail handleNotFound(ResourceNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    /** Mantém rotas inexistentes com o status HTTP 404 padrão. */
    @ExceptionHandler(NoResourceFoundException.class)
    ProblemDetail handleUnknownRoute(NoResourceFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Rota não encontrada.");
    }

    /** Responde 409 para duplicidades e violações de integridade relacional. */
    @ExceptionHandler({ConflictException.class, DataIntegrityViolationException.class})
    ProblemDetail handleConflict(RuntimeException exception) {
        return problem(HttpStatus.CONFLICT, "O recurso já existe ou está relacionado a outro registro.");
    }

    /** Expõe os campos inválidos sem retornar detalhes internos do servidor. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException exception) {
        ProblemDetail detail = problem(HttpStatus.BAD_REQUEST, "Revise os campos enviados.");
        detail.setProperty("fields", exception.getBindingResult().getFieldErrors().stream()
                .collect(java.util.stream.Collectors.toMap(error -> error.getField(),
                        error -> error.getDefaultMessage(), (first, ignored) -> first)));
        return detail;
    }

    /** Converte regras de negócio inválidas em uma resposta 400. */
    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleInvalidArgument(IllegalArgumentException exception) {
        return problem(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    /** Informa falha do serviço externo sem repassar detalhes de rede. */
    @ExceptionHandler(RestClientException.class)
    ProblemDetail handleUpstream(RestClientException exception) {
        return problem(HttpStatus.BAD_GATEWAY, "Não foi possível consultar o serviço FIWARE configurado.");
    }

    /** Evita devolver stack traces ou nomes de classes ao consumidor da API. */
    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception exception) {
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Ocorreu um erro interno ao processar a solicitação.");
    }

    /** Cria o formato comum de erro e inclui o instante em que ocorreu. */
    private ProblemDetail problem(HttpStatus status, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }
}
