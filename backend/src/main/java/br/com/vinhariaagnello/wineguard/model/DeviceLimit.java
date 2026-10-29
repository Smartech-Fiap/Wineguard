package br.com.vinhariaagnello.wineguard.model;

import br.com.vinhariaagnello.wineguard.enums.Metric;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "device_limits", uniqueConstraints = @UniqueConstraint(name = "uk_device_metric", columnNames = {"device_id", "metric"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
/** Guarda os limites configuráveis de uma métrica para um dispositivo. */
public class DeviceLimit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id", nullable = false)
    /** O limite pertence a um dispositivo; o ID é navegado como device.id nos repositórios. */
    private Device device;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Metric metric;

    @Column(name = "minimum_value", precision = 12, scale = 3)
    @Setter
    private BigDecimal minimumValue;

    @Column(name = "maximum_value", precision = 12, scale = 3)
    @Setter
    private BigDecimal maximumValue;

    @Column(nullable = false, length = 20)
    @Setter
    private String unit;

    /** Constrói um limite associado ao dispositivo e à métrica informados. */
    public DeviceLimit(Device device, Metric metric, BigDecimal minimumValue, BigDecimal maximumValue, String unit) {
        this.device = device;
        this.metric = metric;
        this.minimumValue = minimumValue;
        this.maximumValue = maximumValue;
        this.unit = unit;
    }

    /** Expõe o ID associado para DTOs sem incluir a entidade inteira na resposta. */
    public Long getDeviceId() { return device.getId(); }
}
