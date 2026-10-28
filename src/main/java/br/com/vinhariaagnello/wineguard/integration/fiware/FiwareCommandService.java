package br.com.vinhariaagnello.wineguard.integration.fiware;

import br.com.vinhariaagnello.wineguard.model.Device;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class FiwareCommandService {

    private static final Set<String> ALLOWED_COMMANDS =
            Set.of("normal", "temperature", "humidity", "luminosity");

    private final RestClient orion;

    public FiwareCommandService(
            @Qualifier("orionRestClient") RestClient orion) {
        this.orion = orion;
    }

    public void sendAlert(Device device, String command) {
        if (command == null || !ALLOWED_COMMANDS.contains(command)) {
            throw new IllegalArgumentException(
                    "Comando deve ser normal, temperature, humidity ou luminosity.");
        }

        if (!device.isActive()) {
            throw new IllegalArgumentException(
                    "O dispositivo está desativado.");
        }

        orion.put()
                .uri(uriBuilder -> uriBuilder
                        .path("/v2/entities/{id}/attrs/alert")
                        .queryParam("type", device.getFiwareType())
                        .build(device.getFiwareId()))
                .header("Fiware-Service", device.getFiwareService())
                .header("Fiware-ServicePath", device.getFiwareServicePath())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("type", "command", "value", command))
                .retrieve()
                .toBodilessEntity();
    }
}
