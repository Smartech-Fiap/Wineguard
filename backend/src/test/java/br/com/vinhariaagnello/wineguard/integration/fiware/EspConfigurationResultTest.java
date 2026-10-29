package br.com.vinhariaagnello.wineguard.integration.fiware;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EspConfigurationResultTest {

    @Test
    void readsAppliedResult() {
        var result = EspConfigurationResult
                .parse("v1,2,applied")
                .orElseThrow();

        assertEquals(2L, result.revision());
        assertEquals("applied", result.outcome());
        assertFalse(result.rejected());
        assertNull(result.reason());
    }

    @Test
    void readsRepeatedCommandResult() {
        var result = EspConfigurationResult
                .parse("v1,2,already_applied")
                .orElseThrow();

        assertEquals(2L, result.revision());
        assertEquals("already_applied", result.outcome());
        assertFalse(result.rejected());
    }

    @Test
    void readsRejectionObservedOnTheRealDevice() {
        var result = EspConfigurationResult
                .parse("v1,0,rejected,invalid_format")
                .orElseThrow();

        assertEquals(0L, result.revision());
        assertTrue(result.rejected());
        assertEquals("invalid_format", result.reason());
    }

    @Test
    void rejectsUnknownReason() {
        assertTrue(EspConfigurationResult
                .parse("v1,2,rejected,invented_reason")
                .isEmpty());
    }

    @Test
    void rejectsUnsupportedProtocol() {
        assertTrue(EspConfigurationResult
                .parse("v2,2,applied")
                .isEmpty());
    }

    @Test
    void rejectsRevisionOutsideUnsigned32BitRange() {
        assertTrue(EspConfigurationResult
                .parse("v1,4294967296,applied")
                .isEmpty());

        assertTrue(EspConfigurationResult
                .parse("v1,-1,applied")
                .isEmpty());
    }

    @Test
    void rejectsMissingOrExtraFields() {
        assertTrue(EspConfigurationResult
                .parse("v1,2,rejected")
                .isEmpty());

        assertTrue(EspConfigurationResult
                .parse("v1,2,applied,extra")
                .isEmpty());
    }

    @Test
    void rejectsNullAndNonTextValues() {
        assertTrue(EspConfigurationResult.parse(null).isEmpty());
        assertTrue(EspConfigurationResult.parse(123).isEmpty());
    }

    @Test
    void acceptsMaximumRevision() {
        var result = EspConfigurationResult
                .parse("v1,4294967295,applied")
                .orElseThrow();

        assertEquals(4294967295L, result.revision());
    }
}
