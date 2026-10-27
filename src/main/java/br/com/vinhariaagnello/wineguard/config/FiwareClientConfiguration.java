package br.com.vinhariaagnello.wineguard.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(FiwareProperties.class)
/** Cria clientes HTTP independentes para as bases Orion e STH-Comet. */
public class FiwareClientConfiguration {

    /** Configura o RestClient do Orion com sua URL externa. */
    @Bean
    RestClient orionRestClient(FiwareProperties properties) {
        return RestClient.builder().baseUrl(properties.orion().baseUrl()).build();
    }

    /** Configura o RestClient do STH-Comet com sua URL externa. */
    @Bean
    RestClient sthRestClient(FiwareProperties properties) {
        return RestClient.builder().baseUrl(properties.sth().baseUrl()).build();
    }
}
