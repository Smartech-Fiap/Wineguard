package br.com.vinhariaagnello.wineguard.dto;

import br.com.vinhariaagnello.wineguard.model.Device;
import br.com.vinhariaagnello.wineguard.model.DeviceLimit;
import br.com.vinhariaagnello.wineguard.enums.Metric;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class DeviceDtos {
    /** Impede a criação de instâncias utilitárias desta classe de contratos. */
    private DeviceDtos() { }

    /** Campos exigidos para registrar a identidade FIWARE e o ambiente do sensor. */
    public record CreateRequest(@NotBlank @Size(max = 100) String name,
                                @NotBlank @Size(max = 255) String fiwareId,
                                @NotBlank @Size(max = 100) String fiwareType,
                                @NotBlank @Size(max = 100) String fiwareService,
                                @NotBlank @Size(max = 255) String fiwareServicePath,
                                @NotNull Long environmentId) { }

    public record Response(Long id, String name, String fiwareId, String fiwareType,
                          String fiwareService, String fiwareServicePath,
                          Long environmentId, String environmentName, boolean active) {
        /** Converte a entidade para o JSON público sem serializar relações JPA. */
        public static Response from(Device device) {
            return new Response(device.getId(), device.getName(), device.getFiwareId(), device.getFiwareType(),
                    device.getFiwareService(), device.getFiwareServicePath(), device.getEnvironment().getId(),
                    device.getEnvironment().getName(), device.isActive());
        }
    }

    /** Limite configurável de uma métrica ambiental e sua unidade física. */
    public record LimitRequest(@NotNull Metric metric, java.math.BigDecimal minimumValue,
                              java.math.BigDecimal maximumValue, @NotBlank @Size(max = 20) String unit) { }

    public record LimitResponse(Long id, Long deviceId, Metric metric,
                                java.math.BigDecimal minimumValue, java.math.BigDecimal maximumValue, String unit) {
        /** Expõe o limite persistido sem retornar a entidade JPA. */
        public static LimitResponse from(DeviceLimit limit) {
            return new LimitResponse(limit.getId(), limit.getDeviceId(), limit.getMetric(),
                    limit.getMinimumValue(), limit.getMaximumValue(), limit.getUnit());
        }
    }
}
