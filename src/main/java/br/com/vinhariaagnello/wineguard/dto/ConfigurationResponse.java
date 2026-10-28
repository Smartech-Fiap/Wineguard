package br.com.vinhariaagnello.wineguard.dto;

import br.com.vinhariaagnello.wineguard.enums.ConfigurationStatus;
import br.com.vinhariaagnello.wineguard.model.DeviceConfiguration;
import java.math.BigDecimal;
import java.time.Instant;

/** Expõe os valores enviados e a situação da confirmação pelo ESP. */
public record ConfigurationResponse(
        Long id,
        Long deviceId,
        String protocolVersion,
        Long revision,
        BigDecimal temperatureMin,
        BigDecimal temperatureMax,
        BigDecimal humidityMin,
        BigDecimal humidityMax,
        Integer luminosityMax,
        ConfigurationStatus status,
        String rejectionReason,
        Instant createdAt,
        Instant confirmedAt
) {

    public static ConfigurationResponse from(
            DeviceConfiguration configuration) {
        return new ConfigurationResponse(
                configuration.getId(),
                configuration.getDevice().getId(),
                configuration.getProtocolVersion(),
                configuration.getRevision(),
                configuration.getTemperatureMin(),
                configuration.getTemperatureMax(),
                configuration.getHumidityMin(),
                configuration.getHumidityMax(),
                configuration.getLuminosityMax(),
                configuration.getStatus(),
                configuration.getRejectionReason(),
                configuration.getCreatedAt(),
                configuration.getConfirmedAt()
        );
    }
}
