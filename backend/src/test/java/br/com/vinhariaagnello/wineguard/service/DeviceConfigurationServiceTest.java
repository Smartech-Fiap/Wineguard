package br.com.vinhariaagnello.wineguard.service;

import br.com.vinhariaagnello.wineguard.common.ConflictException;
import br.com.vinhariaagnello.wineguard.dto.ConfigurationDtos;
import br.com.vinhariaagnello.wineguard.dto.ConfigurationResponse;
import br.com.vinhariaagnello.wineguard.enums.ConfigurationStatus;
import br.com.vinhariaagnello.wineguard.integration.fiware.FiwareConfigurationCommandService;
import br.com.vinhariaagnello.wineguard.integration.fiware.FiwareReadingsService;
import br.com.vinhariaagnello.wineguard.model.Device;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.springframework.web.client.RestClientException;

class DeviceConfigurationServiceTest {

    private DeviceService devices;
    private ConfigurationRegistrationService registration;
    private ConfigurationStateService state;
    private FiwareConfigurationCommandService commands;
    private FiwareReadingsService readings;
    private ConfigurationAttemptService attempts;
    private Device device;
    private DeviceConfigurationService service;
    private ConfigurationResponse pending;

    @BeforeEach
    void setUp() {
        devices = mock(DeviceService.class);
        registration = mock(ConfigurationRegistrationService.class);
        state = mock(ConfigurationStateService.class);
        commands = mock(FiwareConfigurationCommandService.class);
        readings = mock(FiwareReadingsService.class);
        attempts = mock(ConfigurationAttemptService.class);
        device = mock(Device.class);

        when(device.getId()).thenReturn(1L);
        when(device.isActive()).thenReturn(true);
        when(devices.require(1L)).thenReturn(device);

        pending = response(ConfigurationStatus.PENDING);
        var unconfirmed = response(ConfigurationStatus.UNCONFIRMED);

        when(state.latest(1L)).thenReturn(unconfirmed);
        when(state.refresh(eq(1L), eq(2L), any(), any()))
                .thenReturn(unconfirmed);
        when(readings.current(device)).thenReturn(Map.of(
                "configuration", "v1,1,15,29,40,82,3500"));
        when(attempts.reserve(1L, 2L, true))
                .thenReturn(new ConfigurationAttemptService.Attempt(
                        pending, 1));

        service = new DeviceConfigurationService(
                devices, registration, state, commands, readings, attempts);
    }

    private ConfigurationResponse response(ConfigurationStatus status) {
        var createdAt = Instant.parse("2026-10-29T00:00:00Z");

        return new ConfigurationResponse(
                10L, 1L, "v1", 2L,
                new BigDecimal("15"),
                new BigDecimal("29"),
                new BigDecimal("40"),
                new BigDecimal("82"),
                3500,
                status,
                null,
                createdAt,
                status == ConfigurationStatus.APPLIED
                        ? createdAt.plusSeconds(60)
                        : null);
    }

    @Test
    void retriesSameStoredRevisionWithoutRegisteringAnother() {
        var outcome = service.retry(1L, 2L);

        assertTrue(outcome.sent());
        assertSame(pending, outcome.configuration());

        verify(commands).send(device, pending);
        verify(attempts).finish(1L, 2L, 1);
        verifyNoInteractions(registration);
    }

    @Test
    void doesNotResendWhenTelemetryConfirmsApplication() {
        when(readings.current(device)).thenReturn(Map.of(
                "configuration", "v1,2,15,29,40,82,3500"));
        when(state.refresh(eq(1L), eq(2L), any(), any()))
                .thenReturn(response(ConfigurationStatus.APPLIED));

        var outcome = service.retry(1L, 2L);

        assertFalse(outcome.sent());
        assertEquals(ConfigurationStatus.APPLIED,
                outcome.configuration().status());
        verifyNoInteractions(commands, attempts, registration);
    }

    @Test
    void alreadyAppliedRecordDoesNotContactFiware() {
        when(state.latest(1L))
                .thenReturn(response(ConfigurationStatus.APPLIED));

        var outcome = service.retry(1L, 2L);

        assertFalse(outcome.sent());
        verifyNoInteractions(readings, commands, attempts, registration);
    }

    @Test
    void rejectsPreviouslyRejectedConfiguration() {
        when(state.latest(1L))
                .thenReturn(response(ConfigurationStatus.REJECTED));

        assertThrows(ConflictException.class,
                () -> service.retry(1L, 2L));

        verifyNoInteractions(readings, commands, attempts, registration);
    }

    @Test
    void refusesToOverwriteNewerRevisionReportedByEsp() {
        when(readings.current(device)).thenReturn(Map.of(
                "configuration", "v1,3,15,29,40,82,3500"));

        assertThrows(ConflictException.class,
                () -> service.retry(1L, 2L));

        verifyNoInteractions(commands, attempts, registration);
    }

    @Test
    void refusesSameRevisionWithDifferentActiveValues() {
        when(readings.current(device)).thenReturn(Map.of(
                "configuration", "v1,2,16,29,40,82,3500"));

        assertThrows(ConflictException.class,
                () -> service.retry(1L, 2L));

        verifyNoInteractions(commands, attempts, registration);
    }

    @Test
    void doesNotReserveOrSendWhenOrionCannotBeChecked() {
        when(readings.current(device))
                .thenThrow(new RestClientException("Falha simulada"));

        assertThrows(RestClientException.class,
                () -> service.retry(1L, 2L));

        verifyNoInteractions(commands, attempts, registration);
    }

    @Test
    void releasesReservationEvenWhenSendingFails() {
        doThrow(new RestClientException("Falha simulada"))
                .when(commands).send(device, pending);

        assertThrows(RestClientException.class,
                () -> service.retry(1L, 2L));

        verify(attempts).finish(1L, 2L, 1);
        verify(commands, times(1)).send(device, pending);
        verifyNoInteractions(registration);
    }

    @Test
    void doesNotSendWhenAnotherAttemptOwnsReservation() {
        when(attempts.reserve(1L, 2L, true))
                .thenThrow(new ConflictException("Tentativa em andamento"));

        assertThrows(ConflictException.class,
                () -> service.retry(1L, 2L));

        verifyNoInteractions(commands, registration);
        verify(attempts, never()).finish(any(), any(), anyInt());
    }

    @Test
    void initialSendAlsoUsesReservationAndReleasesIt() {
        var request = new ConfigurationDtos.Request(
                new BigDecimal("15"),
                new BigDecimal("29"),
                new BigDecimal("40"),
                new BigDecimal("82"),
                3500);

        when(registration.register(1L, request)).thenReturn(pending);
        when(attempts.reserve(1L, 2L, false))
                .thenReturn(new ConfigurationAttemptService.Attempt(
                        pending, 1));

        var result = service.send(1L, request);

        assertSame(pending, result);
        verify(registration, times(1)).register(1L, request);
        verify(attempts).reserve(1L, 2L, false);
        verify(commands, times(1)).send(device, pending);
        verify(attempts).finish(1L, 2L, 1);
    }
}
