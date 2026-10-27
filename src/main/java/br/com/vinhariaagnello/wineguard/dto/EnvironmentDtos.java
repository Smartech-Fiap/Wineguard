package br.com.vinhariaagnello.wineguard.dto;

import br.com.vinhariaagnello.wineguard.model.Environment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class EnvironmentDtos {
    /** Impede instanciação da classe que apenas agrupa os contratos JSON. */
    private EnvironmentDtos() { }

    /** Dados necessários para cadastrar um ambiente de armazenamento. */
    public record CreateRequest(@NotBlank @Size(max = 100) String name,
                                @Size(max = 500) String description) { }

    public record Response(Long id, String name, String description) {
        /** Converte a entidade persistida para o contrato de resposta da API. */
        public static Response from(Environment environment) {
            return new Response(environment.getId(), environment.getName(), environment.getDescription());
        }
    }
}
