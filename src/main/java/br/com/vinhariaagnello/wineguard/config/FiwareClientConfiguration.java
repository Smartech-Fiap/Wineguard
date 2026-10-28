package br.com.vinhariaagnello.wineguard.config;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(FiwareProperties.class)
public class FiwareClientConfiguration {

    @Bean
    RestClient orionRestClient(FiwareProperties properties) {
        return createClient(properties.orion().baseUrl());
    }

    @Bean
    RestClient sthRestClient(FiwareProperties properties) {
        return createClient(properties.sth().baseUrl());
    }

    private RestClient createClient(String baseUrl) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        JdkClientHttpRequestFactory factory =
                new JdkClientHttpRequestFactory(httpClient);

        factory.setReadTimeout(Duration.ofSeconds(10));

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }
}
