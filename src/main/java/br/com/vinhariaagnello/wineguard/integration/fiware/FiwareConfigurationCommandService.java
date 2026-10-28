package br.com.vinhariaagnello.wineguard.integration.fiware;

import br.com.vinhariaagnello.wineguard.dto.ConfigurationResponse;
import br.com.vinhariaagnello.wineguard.enums.ConfigurationStatus;
import br.com.vinhariaagnello.wineguard.model.Device;

import java.math.BigDecimal;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
/** Envia uma configuração registrada, sem confirmar sua aplicação no ESP. */
public class FiwareConfigurationCommandService {

    private final RestClient orion;

    public FiwareConfigurationCommandService(
            @Qualifier("orionRestClient") RestClient orion) {
        this.orion = orion;
    }

    public void send(Device device, ConfigurationResponse configuration) {
        if (!device.isActive()) {
            throw new IllegalArgumentException(
                    "O dispositivo está desativado.");
        }

        if (!device.getId().equals(configuration.deviceId())) {
            throw new IllegalArgumentException(
                    "A configuração pertence a outro dispositivo.");
        }

        if (configuration.status() != ConfigurationStatus.PENDING) {
            throw new IllegalArgumentException(
                    "A configuração não está pendente de envio.");
        }

        String value = String.join(",",
                configuration.protocolVersion(),
                configuration.revision().toString(),
                decimal(configuration.temperatureMin()),
                decimal(configuration.temperatureMax()),
                decimal(configuration.humidityMin()),
                decimal(configuration.humidityMax()),
                configuration.luminosityMax().toString());

        orion.put()
                .uri(uriBuilder -> uriBuilder
                        .path("/v2/entities/{id}/attrs/configure")
                        .queryParam("type", device.getFiwareType())
                        .build(device.getFiwareId()))
                .header("Fiware-Service", device.getFiwareService())
                .header("Fiware-ServicePath", device.getFiwareServicePath())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("type", "command", "value", value))
                .retrieve()
                .toBodilessEntity();
    }

    private String decimal(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
