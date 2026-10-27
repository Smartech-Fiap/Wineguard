package br.com.vinhariaagnello.wineguard.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "devices", uniqueConstraints = @UniqueConstraint(name = "uk_devices_fiware", columnNames = {"fiware_service", "fiware_service_path", "fiware_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
/** Representa o vínculo local entre um dispositivo físico e sua entidade FIWARE. */
public class Device {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 100)
    @Column(nullable = false, length = 100)
    private String name;

    @NotBlank
    @Size(max = 255)
    @Column(name = "fiware_id", nullable = false, length = 255)
    private String fiwareId;

    @NotBlank
    @Size(max = 100)
    @Column(name = "fiware_type", nullable = false, length = 100)
    private String fiwareType;

    @NotBlank
    @Size(max = 100)
    @Column(name = "fiware_service", nullable = false, length = 100)
    private String fiwareService;

    @NotBlank
    @Size(max = 255)
    @Column(name = "fiware_service_path", nullable = false, length = 255)
    private String fiwareServicePath;

    @Column(nullable = false)
    @Setter
    private boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "environment_id", nullable = false)
    /** Cada dispositivo fica associado a exatamente um ambiente cadastrado. */
    private Environment environment;

    /** Construtor de cadastro usado pelo serviço após validar o ambiente. */
    public Device(String name, String fiwareId, String fiwareType, String fiwareService,
                  String fiwareServicePath, Environment environment) {
        this.name = name;
        this.fiwareId = fiwareId;
        this.fiwareType = fiwareType;
        this.fiwareService = fiwareService;
        this.fiwareServicePath = fiwareServicePath;
        this.environment = environment;
    }

}
