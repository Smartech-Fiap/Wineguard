package br.com.vinhariaagnello.wineguard.common;

/** Sinaliza que o recurso solicitado não existe no banco da aplicação. */
public class ResourceNotFoundException extends RuntimeException {
    /** Cria a resposta de ausência com mensagem específica do recurso. */
    public ResourceNotFoundException(String message) { super(message); }
}
