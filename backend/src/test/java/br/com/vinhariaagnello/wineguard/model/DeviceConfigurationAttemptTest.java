package br.com.vinhariaagnello.wineguard.model;

import br.com.vinhariaagnello.wineguard.enums.ConfigurationStatus;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class DeviceConfigurationAttemptTest {

    private DeviceConfiguration configuration() {
        return new DeviceConfiguration(
                mock(Device.class),
                2L,
                new BigDecimal("15"),
                new BigDecimal("29"),
                new BigDecimal("40"),
                new BigDecimal("82"),
                3500);
    }

    @Test
    void firstAttemptPreservesRevisionValuesAndCreationDate() {
        var configuration = configuration();
        var createdAt = configuration.getCreatedAt();
        var now = createdAt.plusSeconds(1);

        assertTrue(configuration.beginAttempt(now));

        assertEquals(1, configuration.getAttemptCount());
        assertEquals(now, configuration.getLastAttemptAt());
        assertEquals(now.plusSeconds(60),
                configuration.getDispatchLeaseUntil());
        assertEquals(ConfigurationStatus.PENDING,
                configuration.getStatus());
        assertEquals(2L, configuration.getRevision().longValue());
        assertEquals(createdAt, configuration.getCreatedAt());
        assertEquals(new BigDecimal("15"),
                configuration.getTemperatureMin());
    }

    @Test
    void blocksOverlappingAttempt() {
        var configuration = configuration();
        var now = configuration.getCreatedAt();

        assertTrue(configuration.beginAttempt(now));
        assertFalse(configuration.beginAttempt(now.plusSeconds(1)));
        assertEquals(1, configuration.getAttemptCount());
    }

    @Test
    void requiresThirtySecondsEvenAfterDispatchFinishes() {
        var configuration = configuration();
        var now = configuration.getCreatedAt();

        assertTrue(configuration.beginAttempt(now));
        configuration.finishAttempt(1);

        assertNull(configuration.getDispatchLeaseUntil());
        assertFalse(configuration.beginAttempt(now.plusSeconds(29)));
        assertTrue(configuration.beginAttempt(now.plusSeconds(30)));
        assertEquals(2, configuration.getAttemptCount());
        assertEquals(2L, configuration.getRevision().longValue());
    }

    @Test
    void recoversExpiredLeaseAndIgnoresOldCompletion() {
        var configuration = configuration();
        var now = configuration.getCreatedAt();

        assertTrue(configuration.beginAttempt(now));
        assertFalse(configuration.beginAttempt(now.plusSeconds(59)));
        assertTrue(configuration.beginAttempt(now.plusSeconds(60)));

        var newLease = configuration.getDispatchLeaseUntil();

        configuration.finishAttempt(1);
        assertEquals(newLease,
                configuration.getDispatchLeaseUntil());

        configuration.finishAttempt(2);
        assertNull(configuration.getDispatchLeaseUntil());
    }

    @Test
    void confirmationDeadlineStartsAgainAfterRetry() {
        var configuration = configuration();
        var now = configuration.getCreatedAt();

        assertTrue(configuration.beginAttempt(now));
        configuration.finishAttempt(1);
        assertTrue(configuration.beginAttempt(now.plusSeconds(30)));
        configuration.finishAttempt(2);

        assertFalse(configuration.markUnconfirmed(
                now.plusSeconds(59)));
        assertEquals(ConfigurationStatus.PENDING,
                configuration.getStatus());

        assertTrue(configuration.markUnconfirmed(
                now.plusSeconds(60)));
        assertEquals(ConfigurationStatus.UNCONFIRMED,
                configuration.getStatus());
    }

    @Test
    void neverReopensAppliedOrRejectedConfiguration() {
        var applied = configuration();
        var now = applied.getCreatedAt();

        assertTrue(applied.markApplied(now));
        assertFalse(applied.beginAttempt(now.plusSeconds(120)));
        assertEquals(ConfigurationStatus.APPLIED,
                applied.getStatus());

        var rejected = configuration();
        assertTrue(rejected.markRejected(
                "invalid_values", rejected.getCreatedAt()));
        assertFalse(rejected.beginAttempt(
                rejected.getCreatedAt().plusSeconds(120)));
        assertEquals(ConfigurationStatus.REJECTED,
                rejected.getStatus());
    }

    @Test
    void oldRecordWithoutAttemptUsesOriginalCreationDate() {
        var configuration = configuration();
        var createdAt = configuration.getCreatedAt();

        assertNull(configuration.getLastAttemptAt());
        assertEquals(0, configuration.getAttemptCount());

        assertFalse(configuration.markUnconfirmed(
                createdAt.plusSeconds(29)));
        assertTrue(configuration.markUnconfirmed(
                createdAt.plusSeconds(30)));
    }
}
