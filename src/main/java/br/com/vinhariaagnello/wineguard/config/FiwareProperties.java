package br.com.vinhariaagnello.wineguard.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fiware")
/** Mapeia as URLs e o contexto FIWARE definidos por variáveis de ambiente. */
public record FiwareProperties(Endpoint orion, Endpoint sth) {

    /** Configuração do endereço base e cabeçalhos de um serviço FIWARE. */
    public record Endpoint(String baseUrl, String service, String servicePath) {
    }
}
