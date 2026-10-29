package br.com.vinhariaagnello.wineguard.service;

import br.com.vinhariaagnello.wineguard.dto.ConfigurationDtos;
import br.com.vinhariaagnello.wineguard.dto.ConfigurationResponse;
import br.com.vinhariaagnello.wineguard.integration.fiware.EspConfigurationReport;
import br.com.vinhariaagnello.wineguard.integration.fiware.FiwareConfigurationCommandService;
import br.com.vinhariaagnello.wineguard.integration.fiware.FiwareReadingsService;
import br.com.vinhariaagnello.wineguard.model.Device;
import br.com.vinhariaagnello.wineguard.integration.fiware.EspConfigurationResult;

import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

@Service
@RequiredArgsConstructor
/** Coordena chamadas de rede fora das transações do banco. */
public class DeviceConfigurationService {

    private static final Logger LOG =
            LoggerFactory.getLogger(DeviceConfigurationService.class);

    private final DeviceService devices;
    private final ConfigurationRegistrationService registration;
    private final ConfigurationStateService state;
    private final FiwareConfigurationCommandService commands;
    private final FiwareReadingsService readings;

    public ConfigurationResponse send(
            Long deviceId,
            ConfigurationDtos.Request request) {

        ConfigurationResponse configuration =
                registration.register(deviceId, request);

        Device device = devices.require(deviceId);

        // Se a comunicação falhar, o registro permanece no banco.
        // Não há reenvio automático nem confirmação por resposta HTTP.
        commands.send(device, configuration);

        return configuration;
    }

    public ConfigurationResponse latest(Long deviceId) {
        Device device = devices.require(deviceId);
        ConfigurationResponse configuration = state.latest(deviceId);

        Optional<EspConfigurationReport> report = Optional.empty();
        Optional<EspConfigurationResult> result = Optional.empty();

        try {
            Map<String, Object> current = readings.current(device);

            if (current != null) {
                report = EspConfigurationReport.parse(
                        current.get("configuration"));

                result = EspConfigurationResult.parse(
                        current.get("configure_info"));
            }
        } catch (RestClientException exception) {
            LOG.warn(
                    "Não foi possível reconciliar a configuração "
                            + "do dispositivo {} com o Orion.",
                    deviceId);
        }

        return state.refresh(
                deviceId,
                configuration.revision(),
                report,
                result);
    }
}
