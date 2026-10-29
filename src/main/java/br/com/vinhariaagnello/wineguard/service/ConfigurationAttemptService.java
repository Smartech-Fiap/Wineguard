package br.com.vinhariaagnello.wineguard.service;

import br.com.vinhariaagnello.wineguard.common.ConflictException;
import br.com.vinhariaagnello.wineguard.common.ResourceNotFoundException;
import br.com.vinhariaagnello.wineguard.dto.ConfigurationResponse;
import br.com.vinhariaagnello.wineguard.enums.ConfigurationStatus;
import br.com.vinhariaagnello.wineguard.model.Device;
import br.com.vinhariaagnello.wineguard.model.DeviceConfiguration;
import br.com.vinhariaagnello.wineguard.repository.DeviceConfigurationRepository;
import br.com.vinhariaagnello.wineguard.repository.DeviceRepository;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
/** Reserva envios em transações curtas, sem chamadas de rede. */
public class ConfigurationAttemptService {

    private final DeviceRepository devices;
    private final DeviceConfigurationRepository configurations;
    private final DeviceAccessService access;

    public record Attempt(
            ConfigurationResponse configuration,
            int number
    ) {
    }

    @Transactional
    public Attempt reserve(
            Long deviceId,
            Long revision,
            boolean retry) {

        Device device = devices.findForConfiguration(deviceId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Dispositivo não encontrado."));
        access.requireAccess(device);

        if (!device.isActive()) {
            throw new IllegalArgumentException(
                    "O dispositivo está desativado.");
        }

        DeviceConfiguration configuration = configurations
                .findForUpdate(deviceId, revision)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Configuração não encontrada."));

        DeviceConfiguration latest = configurations
                .findFirstByDevice_IdOrderByRevisionDesc(deviceId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Configuração não encontrada."));

        if (!latest.getRevision().equals(revision)) {
            throw new ConflictException(
                    "Somente a revisão mais recente pode ser enviada.");
        }

        Instant now = Instant.now();

        if (retry) {
            configuration.markUnconfirmed(now);

            if (configuration.getStatus()
                    != ConfigurationStatus.UNCONFIRMED) {
                throw new ConflictException(
                        "O reenvio exige uma configuração "
                                + "sem confirmação após o prazo de espera.");
            }
        } else if (configuration.getAttemptCount() != 0
                || configuration.getStatus()
                    != ConfigurationStatus.PENDING) {
            throw new ConflictException(
                    "O primeiro envio já foi iniciado "
                            + "ou a configuração foi concluída.");
        }

        if (!configuration.beginAttempt(now)) {
            throw new ConflictException(
                    "Existe uma tentativa em andamento "
                            + "ou o prazo para reenvio não terminou.");
        }

        configurations.flush();

        return new Attempt(
                ConfigurationResponse.from(configuration),
                configuration.getAttemptCount());
    }

    @Transactional
    public void finish(
            Long deviceId,
            Long revision,
            int attemptNumber) {

        configurations.findForUpdate(deviceId, revision)
                .ifPresent(configuration ->
                        configuration.finishAttempt(attemptNumber));
    }
}
