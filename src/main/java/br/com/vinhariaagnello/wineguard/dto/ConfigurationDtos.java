package br.com.vinhariaagnello.wineguard.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** Contratos para configurar os limites locais do ESP. */
public final class ConfigurationDtos {

    private ConfigurationDtos() {
    }

    /** Configuração completa; não permite valores ausentes. */
    public record Request(
            @NotNull
            @DecimalMin("-20")
            @DecimalMax("60")
            @Digits(integer = 2, fraction = 3)
            BigDecimal temperatureMin,

            @NotNull
            @DecimalMin("-20")
            @DecimalMax("60")
            @Digits(integer = 2, fraction = 3)
            BigDecimal temperatureMax,

            @NotNull
            @DecimalMin("0")
            @DecimalMax("100")
            @Digits(integer = 3, fraction = 3)
            BigDecimal humidityMin,

            @NotNull
            @DecimalMin("0")
            @DecimalMax("100")
            @Digits(integer = 3, fraction = 3)
            BigDecimal humidityMax,

            @NotNull
            @Min(0)
            @Max(4095)
            Integer luminosityMax
    ) {
    }
}
