package br.com.vinhariaagnello.wineguard.controller;

import br.com.vinhariaagnello.wineguard.enums.Metric;
import br.com.vinhariaagnello.wineguard.dto.DeviceDtos;
import br.com.vinhariaagnello.wineguard.service.DeviceService;
import br.com.vinhariaagnello.wineguard.integration.fiware.FiwareReadingsService;
import br.com.vinhariaagnello.wineguard.dto.CommandDtos;
import br.com.vinhariaagnello.wineguard.integration.fiware.FiwareCommandService;
import br.com.vinhariaagnello.wineguard.integration.fiware.IotaProvisioningService;

import jakarta.validation.Valid;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
/** Endpoints para cadastro de sensores, limites e leituras FIWARE. */
public class DeviceController {
    private final DeviceService service;
    private final FiwareReadingsService readings;
    private final FiwareCommandService commands;
    private final IotaProvisioningService provisioning;

    /** Lista os dispositivos cadastrados com seus ambientes. */
    @GetMapping
    public List<DeviceDtos.Response> findAll() { return service.findAll(); }

    /** Cadastra um dispositivo e responde 201 com o endereço do novo recurso. */
    @PostMapping
    public ResponseEntity<DeviceDtos.Response> create(@Valid @RequestBody DeviceDtos.CreateRequest request) {
        DeviceDtos.Response created = service.create(request);
        return ResponseEntity.created(URI.create("/api/devices/" + created.id())).body(created);
    }

    /** Lista os limites ambientais configurados para um sensor. */
    @GetMapping("/{deviceId}/limits")
    public List<DeviceDtos.LimitResponse> findLimits(@PathVariable Long deviceId) {
        return service.findLimits(deviceId);
    }

    /** Cria ou substitui o limite da métrica informada para o sensor. */
    @PutMapping("/{deviceId}/limits")
    public DeviceDtos.LimitResponse putLimit(@PathVariable Long deviceId,
                                            @Valid @RequestBody DeviceDtos.LimitRequest request) {
        return service.putLimit(deviceId, request);
    }

    /** Consulta no Orion os atributos atuais do dispositivo registrado. */
    @GetMapping("/{deviceId}/readings/current")
    public Map<String, Object> currentReadings(@PathVariable Long deviceId) {
        return readings.current(service.require(deviceId));
    }

    /** Busca no STH-Comet o histórico da métrica no intervalo informado. */
    @GetMapping("/{deviceId}/readings/history/{metric}")
    public Map<String, Object> history(@PathVariable Long deviceId, @PathVariable Metric metric,
                            @RequestParam Instant dateFrom, @RequestParam Instant dateTo) {
        return readings.history(service.require(deviceId), metric, dateFrom, dateTo);
    }
    /** Solicita um alerta remoto pelo Orion, sem confirmar execução física. */
    @PostMapping("/{deviceId}/commands/alert")
    public ResponseEntity<CommandDtos.AlertResponse> sendAlert(
            @PathVariable Long deviceId,
            @Valid @RequestBody CommandDtos.AlertRequest request) {

        commands.sendAlert(service.require(deviceId), request.command());

        return ResponseEntity.accepted().body(
                new CommandDtos.AlertResponse(
                        deviceId, request.command(), "accepted"));
    }
    /** Provisiona no IoT Agent um dispositivo já cadastrado no banco. */
    @PostMapping("/{deviceId}/provision")
    public ResponseEntity<Map<String, Object>> provision(
            @PathVariable Long deviceId) {

        provisioning.provision(service.require(deviceId));

        return ResponseEntity.status(201).body(Map.of(
                "deviceId", deviceId,
                "status", "provisioned",
                "mqttAccess", "not_configured_by_this_operation"
        ));
    }
}
