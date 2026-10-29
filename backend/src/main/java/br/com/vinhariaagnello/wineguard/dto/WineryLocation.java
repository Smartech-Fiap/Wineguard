package br.com.vinhariaagnello.wineguard.dto;

import java.math.BigDecimal;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record WineryLocation(
        @NotBlank @Size(max = 150)
        String wineryName,

        @NotBlank @Size(max = 100)
        String city,

        @NotBlank @Size(max = 100)
        String region,

        @NotBlank @Size(max = 100)
        String country,

        @NotNull
        @DecimalMin("-90") @DecimalMax("90")
        @Digits(integer = 3, fraction = 6)
        BigDecimal latitude,

        @NotNull
        @DecimalMin("-180") @DecimalMax("180")
        @Digits(integer = 3, fraction = 6)
        BigDecimal longitude
) {
}
