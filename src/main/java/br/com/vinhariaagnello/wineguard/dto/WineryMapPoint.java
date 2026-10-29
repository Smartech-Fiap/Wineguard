package br.com.vinhariaagnello.wineguard.dto;

import java.math.BigDecimal;

/** Dados de localização visíveis no mapa para usuários autenticados. */
public record WineryMapPoint(
        Long id,
        String wineryName,
        String city,
        String region,
        String country,
        BigDecimal latitude,
        BigDecimal longitude
) {
}
