package br.com.vinhariaagnello.wineguard.model;

import br.com.vinhariaagnello.wineguard.enums.ConfigurationStatus;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "device_configurations",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_device_configuration_revision",
        columnNames = {"device_id", "revision"}
    )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
/** Guarda os valores de cada envio e a confirmação recebida do ESP. */
public class DeviceConfiguration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;

    @Column(name = "protocol_version", nullable = false, length = 10)
    private String protocolVersion = "v1";

    @Column(nullable = false)
    private Long revision;

    @Column(name = "temperature_min", nullable = false,
            precision = 12, scale = 3)
    private BigDecimal temperatureMin;

    @Column(name = "temperature_max", nullable = false,
            precision = 12, scale = 3)
    private BigDecimal temperatureMax;

    @Column(name = "humidity_min", nullable = false,
            precision = 12, scale = 3)
    private BigDecimal humidityMin;

    @Column(name = "humidity_max", nullable = false,
            precision = 12, scale = 3)
    private BigDecimal humidityMax;

    @Column(name = "luminosity_max", nullable = false)
    private Integer luminosityMax;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ConfigurationStatus status = ConfigurationStatus.PENDING;

    @Column(name = "rejection_reason", length = 100)
    private String rejectionReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    public DeviceConfiguration(
            Device device,
            Long revision,
            BigDecimal temperatureMin,
            BigDecimal temperatureMax,
            BigDecimal humidityMin,
            BigDecimal humidityMax,
            Integer luminosityMax) {
        this.device = device;
        this.revision = revision;
        this.temperatureMin = temperatureMin;
        this.temperatureMax = temperatureMax;
        this.humidityMin = humidityMin;
        this.humidityMax = humidityMax;
        this.luminosityMax = luminosityMax;
    }
    /** Registra ausência de confirmação, preservando o resultado indefinido. */
    public boolean markUnconfirmed(Instant now) {
        if (status != ConfigurationStatus.PENDING
                || now.isBefore(createdAt.plusSeconds(30))) {
            return false;
        }

        status = ConfigurationStatus.UNCONFIRMED;
        return true;
    }

    /** Aceita também uma confirmação recebida após o prazo de espera. */
    public boolean markApplied(Instant now) {
        if (status != ConfigurationStatus.PENDING
                && status != ConfigurationStatus.UNCONFIRMED) {
            return false;
        }

        status = ConfigurationStatus.APPLIED;
        rejectionReason = null;
        confirmedAt = now;
        return true;
    }

    /** Registra uma rejeição informada pelo ESP. */
    public boolean markRejected(String reason, Instant now) {
        if (reason == null || reason.isBlank() || reason.length() > 100) {
            throw new IllegalArgumentException(
                    "Motivo de rejeição inválido.");
        }

        if (status != ConfigurationStatus.PENDING
                && status != ConfigurationStatus.UNCONFIRMED) {
            return false;
        }

        status = ConfigurationStatus.REJECTED;
        rejectionReason = reason;
        confirmedAt = now;
        return true;
    }
}
