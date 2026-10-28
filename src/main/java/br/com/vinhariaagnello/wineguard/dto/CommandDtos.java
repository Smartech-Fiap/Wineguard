package br.com.vinhariaagnello.wineguard.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public final class CommandDtos {

    private CommandDtos() {
    }

    public record AlertRequest(
            @NotBlank
            @Pattern(
                    regexp = "normal|temperature|humidity|luminosity",
                    message = "Comando deve ser normal, temperature, humidity ou luminosity."
            )
            String command
    ) {
    }

    public record AlertResponse(
            Long deviceId,
            String command,
            String status
    ) {
    }
}
