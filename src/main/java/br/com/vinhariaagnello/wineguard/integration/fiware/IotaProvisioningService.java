package br.com.vinhariaagnello.wineguard.integration.fiware;

import br.com.vinhariaagnello.wineguard.common.ConflictException;
import br.com.vinhariaagnello.wineguard.config.FiwareProperties;
import br.com.vinhariaagnello.wineguard.model.Device;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
public class IotaProvisioningService {

    private final RestClient iota;
    private final FiwareProperties properties;

    public IotaProvisioningService(
            @Qualifier("iotaRestClient") RestClient iota,
            FiwareProperties properties) {
        this.iota = iota;
        this.properties = properties;
    }

    public void provision(Device device) {
        validate(device);

        if (exists(device)) {
            throw new ConflictException(
                    "O identificador já existe no IoT Agent.");
        }

        Map<String, Object> payload = Map.of(
                "device_id", device.getMqttDeviceId(),
                "entity_name", device.getFiwareId(),
                "entity_type", device.getFiwareType(),
                "apikey", properties.iota().apiKey(),
                "transport", "MQTT",
                "attributes", List.of(
                        attribute("t", "temperature"),
                        attribute("h", "humidity"),
                        attribute("l", "luminosity")
                ),
                "commands", List.of(
                        Map.of("name", "alert", "type", "command")
                )
        );

        iota.post()
                .uri("/iot/devices")
                .header("fiware-service", device.getFiwareService())
                .header("fiware-servicepath", device.getFiwareServicePath())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("devices", List.of(payload)))
                .retrieve()
                .toBodilessEntity();
    }

    private boolean exists(Device device) {
        try {
            iota.get()
                    .uri("/iot/devices/{id}", device.getMqttDeviceId())
                    .header("fiware-service", device.getFiwareService())
                    .header("fiware-servicepath", device.getFiwareServicePath())
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404) {
                return false;
            }
            throw exception;
        }
    }

    private void validate(Device device) {
        if (!device.isActive()) {
            throw new IllegalArgumentException("O dispositivo está desativado.");
        }

        if (device.getMqttDeviceId() == null ||
                !device.getMqttDeviceId().matches("[A-Za-z0-9_-]{1,100}")) {
            throw new IllegalArgumentException("Identificador MQTT inválido.");
        }

        if (!"WineSensor".equals(device.getFiwareType()) ||
                !properties.orion().service().equals(device.getFiwareService()) ||
                !properties.orion().servicePath().equals(device.getFiwareServicePath())) {
            throw new IllegalArgumentException(
                    "O dispositivo deve pertencer ao grupo WineGuard configurado.");
        }

        if (properties.iota().apiKey() == null ||
                properties.iota().apiKey().isBlank()) {
            throw new IllegalStateException(
                    "A API key do IoT Agent não está configurada.");
        }
    }

    private Map<String, String> attribute(String objectId, String name) {
        return Map.of("object_id", objectId, "name", name, "type", "Number");
    }
}
