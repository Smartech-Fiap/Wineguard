package br.com.vinhariaagnello.wineguard.controller;

import br.com.vinhariaagnello.wineguard.dto.ConfigurationDtos;
import br.com.vinhariaagnello.wineguard.dto.ConfigurationResponse;
import br.com.vinhariaagnello.wineguard.service.DeviceConfigurationService;

import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/devices/{deviceId}/configuration")
@RequiredArgsConstructor
/** Envio e acompanhamento dos limites locais do ESP. */
public class DeviceConfigurationController {

    private final DeviceConfigurationService configurations;

    @Value("${fiware.configuration.enabled:false}")
    private boolean enabled;

    @PostMapping
    public ResponseEntity<?> send(
            @PathVariable Long deviceId,
            @Valid @RequestBody ConfigurationDtos.Request request) {

        if (!enabled) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of(
                            "error",
                            "O envio de configurações ao ESP "
                                    + "ainda não está habilitado."));
        }

        return ResponseEntity.accepted()
                .body(configurations.send(deviceId, request));
    }

    @PostMapping("/{revision}/retry")
    public ResponseEntity<?> retry(
            @PathVariable Long deviceId,
            @PathVariable Long revision) {

        if (!enabled) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of(
                            "error",
                            "O envio de configurações ao ESP "
                                    + "ainda não está habilitado."));
        }

        DeviceConfigurationService.RetryOutcome outcome =
                configurations.retry(deviceId, revision);

        return ResponseEntity
                .status(outcome.sent()
                        ? HttpStatus.ACCEPTED
                        : HttpStatus.OK)
                .body(outcome.configuration());
    }

    @GetMapping
    public ConfigurationResponse latest(
            @PathVariable Long deviceId) {
        return configurations.latest(deviceId);
    }
}
