package br.com.vinhariaagnello.wineguard.service;

import br.com.vinhariaagnello.wineguard.common.ResourceNotFoundException;
import br.com.vinhariaagnello.wineguard.dto.DeviceDtos;
import br.com.vinhariaagnello.wineguard.enums.AppUserRole;
import br.com.vinhariaagnello.wineguard.model.AppUser;
import br.com.vinhariaagnello.wineguard.model.Device;
import br.com.vinhariaagnello.wineguard.model.Environment;
import br.com.vinhariaagnello.wineguard.repository.DeviceLimitRepository;
import br.com.vinhariaagnello.wineguard.repository.DeviceRepository;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeviceServiceOwnershipTest {

    private DeviceRepository devices;
    private CurrentUserService currentUser;
    private AppUser viewer;
    private Environment environment;
    private DeviceService service;

    @BeforeEach
    void setUp() {
        devices = mock(DeviceRepository.class);
        var limits = mock(DeviceLimitRepository.class);
        var environments = mock(EnvironmentService.class);
        currentUser = mock(CurrentUserService.class);
        viewer = mock(AppUser.class);
        environment = mock(Environment.class);

        when(viewer.getId()).thenReturn(10L);
        when(viewer.getRole()).thenReturn(AppUserRole.USER);
        when(currentUser.require()).thenReturn(viewer);
        when(environment.getId()).thenReturn(7L);
        when(environment.getName()).thenReturn("Ambiente de teste");
        when(environments.require(7L)).thenReturn(environment);

        service = new DeviceService(
                devices,
                limits,
                environments,
                currentUser,
                new DeviceAccessService(currentUser));
    }

    private Device sensor(String fiwareId, Long ownerId) {
        Device device = new Device(
                "Sensor de teste",
                fiwareId,
                "mqtt-" + fiwareId,
                "WineSensor",
                "vinheria",
                "/",
                environment);

        AppUser owner = mock(AppUser.class);
        when(owner.getId()).thenReturn(ownerId);
        device.assignOwner(owner);
        return device;
    }

    @Test
    void ordinaryUserListsOnlyOwnDevices() {
        Device own = sensor("MeuSensor", 10L);

        when(devices.findAllByOwner_IdOrderByIdAsc(10L))
                .thenReturn(List.of(own));

        var result = service.findAll();

        assertEquals(1, result.size());
        assertEquals("MeuSensor", result.get(0).fiwareId());
        verify(devices).findAllByOwner_IdOrderByIdAsc(10L);
        verify(devices, never()).findAll();
    }

    @Test
    void administratorListsAllDevices() {
        when(viewer.getRole()).thenReturn(AppUserRole.ADMIN);

        Device first = sensor("SensorA", 10L);
        Device second = sensor("SensorB", 20L);
        when(devices.findAll()).thenReturn(List.of(first, second));

        var result = service.findAll();

        assertEquals(2, result.size());
        verify(devices).findAll();
        verify(devices, never())
                .findAllByOwner_IdOrderByIdAsc(anyLong());
    }

    @Test
    void ownerCanLoadOwnDevice() {
        Device own = sensor("MeuSensor", 10L);
        when(devices.findById(1L)).thenReturn(Optional.of(own));

        assertSame(own, service.require(1L));
    }

    @Test
    void anotherOwnersDeviceIsNotFoundForOrdinaryUser() {
        Device other = sensor("OutroSensor", 20L);
        when(devices.findById(1L)).thenReturn(Optional.of(other));

        assertThrows(ResourceNotFoundException.class,
                () -> service.require(1L));
    }

    @Test
    void administratorCanLoadAnotherOwnersDevice() {
        when(viewer.getRole()).thenReturn(AppUserRole.ADMIN);

        Device other = sensor("OutroSensor", 20L);
        when(devices.findById(1L)).thenReturn(Optional.of(other));

        assertSame(other, service.require(1L));
    }

    @Test
    void newDeviceBelongsToAuthenticatedUser() {
        when(devices.save(any(Device.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var request = new DeviceDtos.CreateRequest(
                "Sensor novo",
                "NovoSensor",
                "novo-sensor",
                "WineSensor",
                "vinheria",
                "/",
                7L);

        service.create(request);

        var captured = ArgumentCaptor.forClass(Device.class);
        verify(devices).save(captured.capture());

        assertSame(viewer, captured.getValue().getOwner());
        assertEquals(10L,
                captured.getValue().getOwner().getId().longValue());
    }
}
