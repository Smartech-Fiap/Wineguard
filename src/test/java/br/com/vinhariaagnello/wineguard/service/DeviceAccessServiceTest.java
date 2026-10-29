package br.com.vinhariaagnello.wineguard.service;

import br.com.vinhariaagnello.wineguard.common.ResourceNotFoundException;
import br.com.vinhariaagnello.wineguard.enums.AppUserRole;
import br.com.vinhariaagnello.wineguard.model.AppUser;
import br.com.vinhariaagnello.wineguard.model.Device;
import br.com.vinhariaagnello.wineguard.model.Environment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.InsufficientAuthenticationException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeviceAccessServiceTest {

    private CurrentUserService currentUser;
    private AppUser viewer;
    private DeviceAccessService service;

    @BeforeEach
    void setUp() {
        currentUser = mock(CurrentUserService.class);
        viewer = account(10L, AppUserRole.USER);
        when(currentUser.require()).thenReturn(viewer);

        service = new DeviceAccessService(currentUser);
    }

    private AppUser account(Long id, AppUserRole role) {
        AppUser user = mock(AppUser.class);
        when(user.getId()).thenReturn(id);
        when(user.getRole()).thenReturn(role);
        return user;
    }

    private Device device() {
        return new Device(
                "Sensor de teste",
                "WineSensorTest",
                "wine-test",
                "WineSensor",
                "vinheria",
                "/",
                mock(Environment.class));
    }

    @Test
    void ownerCanAccessOwnDeviceByUserId() {
        Device device = device();
        device.assignOwner(account(10L, AppUserRole.USER));

        assertDoesNotThrow(() -> service.requireAccess(device));
    }

    @Test
    void ordinaryUserCannotAccessAnotherOwnersDevice() {
        Device device = device();
        device.assignOwner(account(20L, AppUserRole.USER));

        var exception = assertThrows(
                ResourceNotFoundException.class,
                () -> service.requireAccess(device));

        assertEquals("Dispositivo não encontrado.",
                exception.getMessage());
    }

    @Test
    void ordinaryUserCannotAccessLegacyDeviceWithoutOwner() {
        assertThrows(
                ResourceNotFoundException.class,
                () -> service.requireAccess(device()));
    }

    @Test
    void administratorCanAccessAnotherOwnersDevice() {
        when(viewer.getRole()).thenReturn(AppUserRole.ADMIN);
        Device device = device();
        device.assignOwner(account(20L, AppUserRole.USER));

        assertDoesNotThrow(() -> service.requireAccess(device));
    }

    @Test
    void administratorCanAccessLegacyDeviceWithoutOwner() {
        when(viewer.getRole()).thenReturn(AppUserRole.ADMIN);

        assertDoesNotThrow(() -> service.requireAccess(device()));
    }

    @Test
    void missingAuthenticationDoesNotAllowAccess() {
        when(currentUser.require()).thenThrow(
                new InsufficientAuthenticationException(
                        "Autenticação necessária."));

        assertThrows(
                InsufficientAuthenticationException.class,
                () -> service.requireAccess(device()));
    }
}
