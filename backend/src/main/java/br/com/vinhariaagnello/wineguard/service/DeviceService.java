package br.com.vinhariaagnello.wineguard.service;

import br.com.vinhariaagnello.wineguard.common.ConflictException;
import br.com.vinhariaagnello.wineguard.common.ResourceNotFoundException;
import br.com.vinhariaagnello.wineguard.enums.AppUserRole;
import br.com.vinhariaagnello.wineguard.model.AppUser;
import br.com.vinhariaagnello.wineguard.model.Device;
import br.com.vinhariaagnello.wineguard.model.DeviceLimit;
import br.com.vinhariaagnello.wineguard.dto.DeviceDtos;
import br.com.vinhariaagnello.wineguard.repository.DeviceRepository;
import br.com.vinhariaagnello.wineguard.repository.DeviceLimitRepository;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class DeviceService {
    private final DeviceRepository devices;
    private final DeviceLimitRepository limits;
    private final EnvironmentService environments;
    private final CurrentUserService currentUser;
    private final DeviceAccessService access;

    @Transactional(readOnly = true)
    /** Lista dispositivos para alimentar o painel sem expor entidades JPA. */
    public List<DeviceDtos.Response> findAll() {
        AppUser user = currentUser.require();

        List<Device> visible = user.getRole() == AppUserRole.ADMIN
                ? devices.findAll()
                : devices.findAllByOwner_IdOrderByIdAsc(user.getId());

        return visible.stream()
                .map(DeviceDtos.Response::from)
                .toList();
    }

    @Transactional(readOnly = true)
    /** Localiza um dispositivo ou interrompe o fluxo com erro 404. */
    public Device require(Long id) {
        Device device = devices.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Dispositivo não encontrado."));

        access.requireAccess(device);
        return device;
    }

    /** Valida unicidade FIWARE, associa o ambiente e persiste o sensor. */
    public DeviceDtos.Response create(DeviceDtos.CreateRequest request) {
        AppUser owner = currentUser.require();
        if (devices.existsByFiwareServiceAndFiwareServicePathAndFiwareId(
                request.fiwareService(), request.fiwareServicePath(), request.fiwareId())) {
            throw new ConflictException("O dispositivo FIWARE já está cadastrado.");
        }

	if (devices.existsByFiwareServiceAndFiwareServicePathAndMqttDeviceId(
                request.fiwareService(),
                request.fiwareServicePath(),
                request.mqttDeviceId())) {
            throw new ConflictException(
                    "O identificador MQTT já está cadastrado nesse contexto FIWARE.");
        }

        Device device = new Device(
                request.name(),
                request.fiwareId(),
                request.mqttDeviceId(),
                request.fiwareType(),
                request.fiwareService(),
                request.fiwareServicePath(),
                environments.require(request.environmentId())
        );
        device.assignOwner(owner);
        return DeviceDtos.Response.from(devices.save(device));
    }

    @Transactional(readOnly = true)
    /** Retorna limites ordenados por métrica para o dispositivo informado. */
    public List<DeviceDtos.LimitResponse> findLimits(Long deviceId) {
        require(deviceId);
        return limits.findAllByDevice_IdOrderByMetric(deviceId).stream().map(DeviceDtos.LimitResponse::from).toList();
    }

    /** Cria ou atualiza um limite, validando intervalo e presença de valores. */
    public DeviceDtos.LimitResponse putLimit(Long deviceId, DeviceDtos.LimitRequest request) {
        Device device = require(deviceId);
        if (request.minimumValue() == null && request.maximumValue() == null) {
            throw new IllegalArgumentException("Informe ao menos o limite mínimo ou máximo.");
        }
        BigDecimal minimum = request.minimumValue();
        BigDecimal maximum = request.maximumValue();
        if (minimum != null && maximum != null && minimum.compareTo(maximum) > 0) {
            throw new IllegalArgumentException("O limite mínimo deve ser menor ou igual ao máximo.");
        }
        DeviceLimit limit = limits.findByDevice_IdAndMetric(deviceId, request.metric()).orElseGet(
                () -> new DeviceLimit(device, request.metric(), minimum, maximum, request.unit()));
        limit.setMinimumValue(minimum);
        limit.setMaximumValue(maximum);
        limit.setUnit(request.unit());
        return DeviceDtos.LimitResponse.from(limits.save(limit));
    }

    /** Remove o cadastro local após a remoção no FIWARE. */
    public void deleteLocalAfterFiwareRemoval(Long deviceId) {
        Device device = require(deviceId);
        devices.delete(device);
        devices.flush();
    }
}
