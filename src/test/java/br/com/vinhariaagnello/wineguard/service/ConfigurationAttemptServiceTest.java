package br.com.vinhariaagnello.wineguard.service;

import br.com.vinhariaagnello.wineguard.common.ConflictException;
import br.com.vinhariaagnello.wineguard.enums.ConfigurationStatus;
import br.com.vinhariaagnello.wineguard.model.Device;
import br.com.vinhariaagnello.wineguard.model.DeviceConfiguration;
import br.com.vinhariaagnello.wineguard.repository.DeviceConfigurationRepository;
import br.com.vinhariaagnello.wineguard.repository.DeviceRepository;

import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConfigurationAttemptServiceTest {

    private Device device;
    private DeviceRepository devices;
    private DeviceConfigurationRepository configurations;
    private DeviceConfiguration configuration;
    private ConfigurationAttemptService service;

    @BeforeEach
    void setUp() {
        device = mock(Device.class);
        devices = mock(DeviceRepository.class);
        configurations = mock(DeviceConfigurationRepository.class);

        when(device.getId()).thenReturn(1L);
        when(device.isActive()).thenReturn(true);

        configuration = configuration(2L);

        when(devices.findForConfiguration(1L))
                .thenReturn(Optional.of(device));
        when(configurations.findForUpdate(1L, 2L))
                .thenReturn(Optional.of(configuration));
        when(configurations.findFirstByDevice_IdOrderByRevisionDesc(1L))
                .thenReturn(Optional.of(configuration));

        service = new ConfigurationAttemptService(
                devices, configurations);
    }

    private DeviceConfiguration configuration(Long revision) {
        return new DeviceConfiguration(
                device,
                revision,
                new BigDecimal("15"),
                new BigDecimal("29"),
                new BigDecimal("40"),
                new BigDecimal("82"),
                3500);
    }

    @Test
    void reservesFirstAttemptWithoutCreatingAnotherRevision() {
        var attempt = service.reserve(1L, 2L, false);

        assertEquals(1, attempt.number());
        assertEquals(2L,
                attempt.configuration().revision().longValue());
        assertEquals(ConfigurationStatus.PENDING,
                attempt.configuration().status());
        assertNotNull(configuration.getDispatchLeaseUntil());

        verify(configurations).flush();
        verify(configurations, never()).save(any());
    }

    @Test
    void rejectsSecondReservationWhileAttemptIsActive() {
        service.reserve(1L, 2L, false);

        assertThrows(ConflictException.class,
                () -> service.reserve(1L, 2L, true));

        assertEquals(1, configuration.getAttemptCount());
    }

    @Test
    void refusesToReopenAppliedConfiguration() {
        configuration.markApplied(configuration.getCreatedAt());

        assertThrows(ConflictException.class,
                () -> service.reserve(1L, 2L, true));

        assertEquals(ConfigurationStatus.APPLIED,
                configuration.getStatus());
        assertEquals(0, configuration.getAttemptCount());
    }

    @Test
    void retriesLegacyUnconfirmedRecordWithSameRevisionAndValues() {
        configuration.markUnconfirmed(
                configuration.getCreatedAt().plusSeconds(30));

        var attempt = service.reserve(1L, 2L, true);

        assertEquals(2L,
                attempt.configuration().revision().longValue());
        assertEquals(new BigDecimal("15"),
                attempt.configuration().temperatureMin());
        assertEquals(1, attempt.number());
        assertEquals(ConfigurationStatus.PENDING,
                configuration.getStatus());
        assertNotNull(configuration.getLastAttemptAt());
    }

    @Test
    void rejectsRevisionThatIsNoLongerTheLatest() {
        when(configurations.findFirstByDevice_IdOrderByRevisionDesc(1L))
                .thenReturn(Optional.of(configuration(3L)));

        assertThrows(ConflictException.class,
                () -> service.reserve(1L, 2L, false));

        assertEquals(0, configuration.getAttemptCount());
        verify(configurations, never()).flush();
    }

    @Test
    void rejectsInactiveDeviceBeforeReservingAttempt() {
        when(device.isActive()).thenReturn(false);

        assertThrows(IllegalArgumentException.class,
                () -> service.reserve(1L, 2L, false));

        verifyNoInteractions(configurations);
    }
}
