package br.com.vinhariaagnello.wineguard.dto;

import br.com.vinhariaagnello.wineguard.enums.AppUserRole;
import br.com.vinhariaagnello.wineguard.model.AppUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AppUserDtos {

    private AppUserDtos() {
    }

    public record CreateRequest(
            @NotBlank
            @Size(min = 3, max = 100)
            @Pattern(
                    regexp = "[A-Za-z0-9._-]+",
                    message = "Use apenas letras sem acentos, números, ponto, hífen ou sublinhado.")
            String username,

            @NotBlank
            @Size(min = 12, max = 72,
                    message = "A senha deve ter entre 12 e 72 caracteres.")
            String password,

            @NotNull(message = "Informe a localização da vinícola.")
            @Valid
            WineryLocation location
    ) {
        @Override
        public String toString() {
            return "CreateRequest[username=" + username
                    + ", password=<redacted>]";
        }
    }

    /** Resposta administrativa, sem senha ou hash. */
    public record Response(
            Long id,
            String username,
            AppUserRole role,
            boolean enabled,
            WineryLocation location
    ) {
        public static Response from(AppUser user) {
            WineryLocation location = user.getWineryName() == null
                    ? null
                    : new WineryLocation(
                            user.getWineryName(),
                            user.getCity(),
                            user.getRegion(),
                            user.getCountry(),
                            user.getLatitude(),
                            user.getLongitude());

            return new Response(
                    user.getId(),
                    user.getUsername(),
                    user.getRole(),
                    user.isEnabled(),
                    location);
        }
    }
}
