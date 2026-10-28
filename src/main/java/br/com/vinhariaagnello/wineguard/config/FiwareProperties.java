package br.com.vinhariaagnello.wineguard.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fiware")
public record FiwareProperties(
        Endpoint orion,
        Endpoint sth,
        IotaEndpoint iota
) {

    public record Endpoint(
            String baseUrl,
            String service,
            String servicePath
    ) {
    }

    public record IotaEndpoint(
            String baseUrl,
            String apiKey
    ) {
    }
}
