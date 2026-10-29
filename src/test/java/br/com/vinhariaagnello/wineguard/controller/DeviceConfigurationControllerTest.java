package br.com.vinhariaagnello.wineguard.controller;

import br.com.vinhariaagnello.wineguard.common.ConflictException;
import br.com.vinhariaagnello.wineguard.dto.ConfigurationResponse;
import br.com.vinhariaagnello.wineguard.enums.ConfigurationStatus;
import br.com.vinhariaagnello.wineguard.service.DeviceConfigurationService;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeviceConfigurationControllerTest {

    private DeviceConfigurationService service;
    private DeviceConfigurationController controller;

    @BeforeEach
    void setUp() {
        service = mock(DeviceConfigurationService.class);
        controller = new DeviceConfigurationController(service);
    }

    private ConfigurationResponse configuration(
            ConfigurationStatus status) {

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
    void disabledFeatureReturns503WithoutCallingService() {
        var response = controller.retry(1L, 2L);

        assertEquals(503, response.getStatusCode().value());
        verifyNoInteractions(service);
    }

    @Test
    void dispatchedRetryReturns202WithSameConfiguration() {
        ReflectionTestUtils.setField(controller, "enabled", true);
        var pending = configuration(ConfigurationStatus.PENDING);

        when(service.retry(1L, 2L))
                .thenReturn(
                        new DeviceConfigurationService.RetryOutcome(
                                true, pending));

        var response = controller.retry(1L, 2L);

        assertEquals(202, response.getStatusCode().value());
        assertSame(pending, response.getBody());
        verify(service, times(1)).retry(1L, 2L);
    }

    @Test
    void alreadyAppliedRevisionReturns200WithoutNewDispatch() {
        ReflectionTestUtils.setField(controller, "enabled", true);
        var applied = configuration(ConfigurationStatus.APPLIED);

        when(service.retry(1L, 2L))
                .thenReturn(
                        new DeviceConfigurationService.RetryOutcome(
                                false, applied));

        var response = controller.retry(1L, 2L);

        assertEquals(200, response.getStatusCode().value());
        assertSame(applied, response.getBody());
    }

    @Test
    void propagatesConflictForTheExceptionHandler() {
        ReflectionTestUtils.setField(controller, "enabled", true);

        when(service.retry(1L, 2L))
                .thenThrow(new ConflictException(
                        "Tentativa em andamento."));

        assertThrows(ConflictException.class,
                () -> controller.retry(1L, 2L));

        verify(service, times(1)).retry(1L, 2L);
    }
}
