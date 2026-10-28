package br.com.vinhariaagnello.wineguard.service;

import br.com.vinhariaagnello.wineguard.integration.fiware.IotaProvisioningService;
import br.com.vinhariaagnello.wineguard.model.Device;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Service
public class DeviceRemovalService {

    private final DeviceService devices;
    private final IotaProvisioningService provisioning;
    private final RestClient orion;

    public DeviceRemovalService(
            DeviceService devices,
            IotaProvisioningService provisioning,
            @Qualifier("orionRestClient") RestClient orion) {
        this.devices = devices;
        this.provisioning = provisioning;
        this.orion = orion;
    }

    public void remove(Long deviceId) {
        Device device = devices.require(deviceId);

        provisioning.removeProvisioning(device);
        removeOrionEntity(device);

        devices.deleteLocalAfterFiwareRemoval(deviceId);
    }

    private void removeOrionEntity(Device device) {
        try {
            orion.delete()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v2/entities/{id}")
                            .queryParam("type", device.getFiwareType())
                            .build(device.getFiwareId()))
                    .header("fiware-service", device.getFiwareService())
                    .header("fiware-servicepath", device.getFiwareServicePath())
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() != 404) {
                throw exception;
            }
        }
    }
}
