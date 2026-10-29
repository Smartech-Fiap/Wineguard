package br.com.vinhariaagnello.wineguard.integration.fiware;

import java.util.Optional;
import java.util.Set;

/** Resultado de execução recebido em configure_info. */
public record EspConfigurationResult(
        long revision,
        String outcome,
        String reason
) {

    private static final Set<String> REJECTION_REASONS = Set.of(
            "invalid_values",
            "stale_revision",
            "revision_conflict",
            "storage_error",
            "invalid_format",
            "unsupported_version"
    );

    public static Optional<EspConfigurationResult> parse(Object raw) {
        if (!(raw instanceof String text) || text.length() > 192) {
            return Optional.empty();
        }

        String[] fields = text.split(",", -1);

        if (fields.length < 3
                || !"v1".equals(fields[0])
                || !fields[1].matches("[0-9]{1,10}")) {
            return Optional.empty();
        }

        try {
            long revision = Long.parseLong(fields[1]);

            if (revision > 4294967295L) {
                return Optional.empty();
            }

            if (fields.length == 3
                    && ("applied".equals(fields[2])
                    || "already_applied".equals(fields[2]))) {
                return Optional.of(new EspConfigurationResult(
                        revision, fields[2], null));
            }

            if (fields.length == 4
                    && "rejected".equals(fields[2])
                    && REJECTION_REASONS.contains(fields[3])) {
                return Optional.of(new EspConfigurationResult(
                        revision, fields[2], fields[3]));
            }

            return Optional.empty();
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    public boolean rejected() {
        return "rejected".equals(outcome);
    }
}
