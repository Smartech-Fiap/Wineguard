package br.com.vinhariaagnello.wineguard.common;

/** Sinaliza uma operação que conflita com um registro já existente. */
public class ConflictException extends RuntimeException {
    /** Cria o conflito com mensagem entendida pelo consumidor da API. */
    public ConflictException(String message) { super(message); }
}
