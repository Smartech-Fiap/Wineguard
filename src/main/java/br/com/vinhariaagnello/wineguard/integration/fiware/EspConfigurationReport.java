package br.com.vinhariaagnello.wineguard.integration.fiware;

import java.math.BigDecimal;
import java.util.Optional;

/** Configuração ativa informada pelo ESP na telemetria. */
public record EspConfigurationReport(
        String protocolVersion,
        long revision,
        BigDecimal temperatureMin,
        BigDecimal temperatureMax,
        BigDecimal humidityMin,
        BigDecimal humidityMax,
        int luminosityMax
) {

    public static Optional<EspConfigurationReport> parse(Object raw) {
        if (!(raw instanceof String text) || text.length() > 192) {
            return Optional.empty();
        }

        String[] fields = text.split(",", -1);

        if (fields.length != 7 || !"v1".equals(fields[0])) {
            return Optional.empty();
        }

        if (!fields[1].matches("[0-9]{1,10}")) {
            return Optional.empty();
        }

        for (int i = 2; i <= 5; i++) {
            if (!fields[i].matches("-?[0-9]{1,3}(\\.[0-9]{1,3})?")) {
                return Optional.empty();
            }
        }

        if (!fields[6].matches("[0-9]{1,4}")) {
            return Optional.empty();
        }

        try {
            long revision = Long.parseLong(fields[1]);
            int lightMax = Integer.parseInt(fields[6]);

            if (revision > 4294967295L || lightMax > 4095) {
                return Optional.empty();
            }

            return Optional.of(new EspConfigurationReport(
                    fields[0],
                    revision,
                    new BigDecimal(fields[2]),
                    new BigDecimal(fields[3]),
                    new BigDecimal(fields[4]),
                    new BigDecimal(fields[5]),
                    lightMax
            ));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }
}
