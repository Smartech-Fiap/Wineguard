package br.com.vinhariaagnello.wineguard.enums;

/** Situação da configuração enviada ao ESP. */
public enum ConfigurationStatus {
    /** Envio registrado, aguardando confirmação. */
    PENDING,

    /** Aplicação confirmada pelo ESP. */
    APPLIED,

    /** Configuração rejeitada pelo ESP. */
    REJECTED,

    /** Prazo de confirmação encerrado; resultado ainda desconhecido. */
    UNCONFIRMED
}
