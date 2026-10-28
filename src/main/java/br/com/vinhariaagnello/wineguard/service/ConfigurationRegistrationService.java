package br.com.vinhariaagnello.wineguard.service;

import br.com.vinhariaagnello.wineguard.common.ConflictException;
import br.com.vinhariaagnello.wineguard.common.ResourceNotFoundException;
import br.com.vinhariaagnello.wineguard.dto.ConfigurationDtos;
import br.com.vinhariaagnello.wineguard.dto.ConfigurationResponse;
import br.com.vinhariaagnello.wineguard.enums.ConfigurationStatus;
import br.com.vinhariaagnello.wineguard.model.Device;
import br.com.vinhariaagnello.wineguard.model.DeviceConfiguration;
import br.com.vinhariaagnello.wineguard.repository.DeviceConfigurationRepository;
import br.com.vinhariaagnello.wineguard.repository.DeviceRepository;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
/** Registra configurações sem realizar chamadas de rede na transação. */
public class ConfigurationRegistrationService {

    private static final long MAX_REVISION = 4294967295L;

    private final DeviceRepository devices;
    private final DeviceConfigurationRepository configurations;

    @Transactional
    public ConfigurationResponse register(
            Long deviceId,
            @NotNull @Valid ConfigurationDtos.Request request) {

        Device device = devices.findForConfiguration(deviceId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Dispositivo não encontrado."));

        if (!device.isActive()) {
            throw new IllegalArgumentException(
                    "O dispositivo está inativo.");
        }

        if (device.getMqttDeviceId() == null
                || device.getMqttDeviceId().isBlank()) {
            throw new IllegalArgumentException(
                    "O dispositivo não possui identificador MQTT.");
        }

        validateIntervals(request);

        boolean unresolved = configurations.existsByDevice_IdAndStatusIn(
                deviceId,
                List.of(
                        ConfigurationStatus.PENDING,
                        ConfigurationStatus.UNCONFIRMED));

        if (unresolved) {
            throw new ConflictException(
                    "Existe uma configuração aguardando confirmação.");
        }

        long previousRevision = configurations
                .findFirstByDevice_IdOrderByRevisionDesc(deviceId)
                .map(DeviceConfiguration::getRevision)
                .orElse(0L);

        if (previousRevision >= MAX_REVISION) {
            throw new ConflictException(
                    "O limite de revisões do dispositivo foi atingido.");
        }

        DeviceConfiguration configuration = new DeviceConfiguration(
                device,
                previousRevision + 1,
                request.temperatureMin(),
                request.temperatureMax(),
                request.humidityMin(),
                request.humidityMax(),
                request.luminosityMax());

        return ConfigurationResponse.from(
                configurations.saveAndFlush(configuration));
    }

    private void validateIntervals(ConfigurationDtos.Request request) {
        BigDecimal temperatureRange = request.temperatureMax()
                .subtract(request.temperatureMin());

        if (temperatureRange.compareTo(new BigDecimal("2")) <= 0) {
            throw new IllegalArgumentException(
                    "O intervalo de temperatura deve ser maior que 2 °C.");
        }

        BigDecimal humidityRange = request.humidityMax()
                .subtract(request.humidityMin());

        if (humidityRange.compareTo(new BigDecimal("6")) <= 0) {
            throw new IllegalArgumentException(
                    "O intervalo de umidade deve ser maior que "
                            + "6 pontos percentuais.");
        }

        if (request.luminosityMax() < 200) {
            throw new IllegalArgumentException(
                    "O limite de luminosidade deve ser de pelo menos "
                            + "200 ADC.");
        }
    }
}
