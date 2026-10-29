package br.com.vinhariaagnello.wineguard.service;

import br.com.vinhariaagnello.wineguard.dto.ConfigurationDtos;
import br.com.vinhariaagnello.wineguard.dto.ConfigurationResponse;
import br.com.vinhariaagnello.wineguard.integration.fiware.EspConfigurationReport;
import br.com.vinhariaagnello.wineguard.integration.fiware.FiwareConfigurationCommandService;
import br.com.vinhariaagnello.wineguard.integration.fiware.FiwareReadingsService;
import br.com.vinhariaagnello.wineguard.model.Device;
import br.com.vinhariaagnello.wineguard.integration.fiware.EspConfigurationResult;
import br.com.vinhariaagnello.wineguard.common.ConflictException;
import br.com.vinhariaagnello.wineguard.enums.ConfigurationStatus;

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
    private final ConfigurationAttemptService attempts;

    public ConfigurationResponse send(
            Long deviceId,
            ConfigurationDtos.Request request) {

        Device device = devices.require(deviceId);

        ConfigurationResponse configuration =
                registration.register(deviceId, request);

        ConfigurationAttemptService.Attempt attempt =
                attempts.reserve(
                        deviceId,
                        configuration.revision(),
                        false);

        return dispatch(device, attempt);
    }

    public record RetryOutcome(
            boolean sent,
            ConfigurationResponse configuration
    ) {
    }

    public RetryOutcome retry(Long deviceId, Long revision) {
        if (revision == null || revision < 1 || revision > 4294967295L) {
            throw new IllegalArgumentException("Revisão inválida.");
        }

        Device device = devices.require(deviceId);

        if (!device.isActive()) {
            throw new IllegalArgumentException(
                    "O dispositivo está desativado.");
        }

        ConfigurationResponse configuration = state.latest(deviceId);

        if (!configuration.revision().equals(revision)) {
            throw new ConflictException(
                    "Somente a revisão mais recente pode ser reenviada.");
        }

        if (configuration.status() == ConfigurationStatus.APPLIED) {
            return new RetryOutcome(false, configuration);
        }

        if (configuration.status() == ConfigurationStatus.REJECTED) {
            throw new ConflictException(
                    "Uma configuração rejeitada não pode ser reenviada.");
        }

        // Diferentemente da consulta comum, falha no Orion interrompe
        // o reenvio. Não prosseguimos sem verificar seu estado.
        Map<String, Object> current = readings.current(device);

        if (current == null) {
            throw new RestClientException(
                    "Resposta vazia ao consultar o Orion.");
        }

        EspConfigurationReport report = EspConfigurationReport
                .parse(current.get("configuration"))
                .orElseThrow(() ->
                        new ConflictException(
                                "O ESP ainda não informou "
                                        + "uma configuração válida."));

        Optional<EspConfigurationResult> result =
                EspConfigurationResult.parse(
                        current.get("configure_info"));

        ConfigurationResponse refreshed = state.refresh(
                deviceId,
                revision,
                Optional.of(report),
                result);

        if (refreshed.status() == ConfigurationStatus.APPLIED) {
            return new RetryOutcome(false, refreshed);
        }

        if (refreshed.status() == ConfigurationStatus.REJECTED) {
            throw new ConflictException(
                    "O ESP rejeitou esta configuração.");
        }

        if (report.revision() >= revision.longValue()) {
            throw new ConflictException(
                    "A revisão ativa do ESP exige reconciliação "
                            + "antes de outro envio.");
        }

        ConfigurationAttemptService.Attempt attempt =
                attempts.reserve(deviceId, revision, true);

        return new RetryOutcome(true, dispatch(device, attempt));
    }

    /** Envia fora da transação e libera somente a tentativa reservada. */
    private ConfigurationResponse dispatch(
            Device device,
            ConfigurationAttemptService.Attempt attempt) {

        ConfigurationResponse configuration = attempt.configuration();

        try {
            commands.send(device, configuration);
            return configuration;
        } finally {
            try {
                attempts.finish(
                        device.getId(),
                        configuration.revision(),
                        attempt.number());
            } catch (RuntimeException exception) {
                LOG.warn(
                        "Não foi possível liberar a tentativa do "
                                + "dispositivo {}, revisão {}. "
                                + "O bloqueio temporário expirará.",
                        device.getId(),
                        configuration.revision());
            }
        }
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
