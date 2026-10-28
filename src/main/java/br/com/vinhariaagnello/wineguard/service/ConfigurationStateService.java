package br.com.vinhariaagnello.wineguard.service;

import br.com.vinhariaagnello.wineguard.common.ResourceNotFoundException;
import br.com.vinhariaagnello.wineguard.dto.ConfigurationResponse;
import br.com.vinhariaagnello.wineguard.integration.fiware.EspConfigurationReport;
import br.com.vinhariaagnello.wineguard.model.DeviceConfiguration;
import br.com.vinhariaagnello.wineguard.repository.DeviceConfigurationRepository;

import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
/** Atualiza o estado com base na configuração ativa informada pelo ESP. */
public class ConfigurationStateService {

    private final DeviceConfigurationRepository configurations;

    @Transactional
    public ConfigurationResponse refresh(
            Long deviceId,
            Long revision,
            Optional<EspConfigurationReport> report) {

        DeviceConfiguration configuration = configurations
                .findForUpdate(deviceId, revision)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Configuração não encontrada."));

        Instant now = Instant.now();

        if (report.isPresent()
                && matches(configuration, report.get())) {
            configuration.markApplied(now);
        } else {
            configuration.markUnconfirmed(now);
        }

        return ConfigurationResponse.from(configuration);
    }

    @Transactional(readOnly = true)
    public ConfigurationResponse latest(Long deviceId) {
        return configurations
                .findFirstByDevice_IdOrderByRevisionDesc(deviceId)
                .map(ConfigurationResponse::from)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Nenhuma configuração foi enviada "
                                        + "para este dispositivo."));
    }

    private boolean matches(
            DeviceConfiguration expected,
            EspConfigurationReport actual) {

        return expected.getProtocolVersion()
                        .equals(actual.protocolVersion())
                && expected.getRevision().longValue() == actual.revision()
                && expected.getTemperatureMin()
                        .compareTo(actual.temperatureMin()) == 0
                && expected.getTemperatureMax()
                        .compareTo(actual.temperatureMax()) == 0
                && expected.getHumidityMin()
                        .compareTo(actual.humidityMin()) == 0
                && expected.getHumidityMax()
                        .compareTo(actual.humidityMax()) == 0
                && expected.getLuminosityMax().intValue()
                        == actual.luminosityMax();
    }
}
