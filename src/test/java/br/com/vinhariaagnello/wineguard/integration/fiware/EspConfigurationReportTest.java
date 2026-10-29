package br.com.vinhariaagnello.wineguard.integration.fiware;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EspConfigurationReportTest {

    @Test
    void readsTelemetryObservedOnTheRealDevice() {
        var report = EspConfigurationReport
                .parse("v1,1,15.0,29.0,40.0,82.0,3500")
                .orElseThrow();

        assertEquals("v1", report.protocolVersion());
        assertEquals(1L, report.revision());
        assertDecimal("15", report.temperatureMin());
        assertDecimal("29", report.temperatureMax());
        assertDecimal("40", report.humidityMin());
        assertDecimal("82", report.humidityMax());
        assertEquals(3500, report.luminosityMax());
    }

    @Test
    void acceptsDefaultConfigurationWithRevisionZero() {
        var report = EspConfigurationReport
                .parse("v1,0,15.0,29.0,40.0,82.0,3500")
                .orElseThrow();

        assertEquals(0L, report.revision());
    }

    @Test
    void preservesNegativeTemperatureAndZeroValues() {
        var report = EspConfigurationReport
                .parse("v1,2,-0.5,29.0,0.0,82.0,200")
                .orElseThrow();

        assertDecimal("-0.5", report.temperatureMin());
        assertDecimal("0", report.humidityMin());
        assertEquals(200, report.luminosityMax());
    }

    @Test
    void rejectsIncompleteAndExtraFields() {
        assertTrue(EspConfigurationReport
                .parse("v1,2,15,29,40,82")
                .isEmpty());

        assertTrue(EspConfigurationReport
                .parse("v1,2,15,29,40,82,3500,extra")
                .isEmpty());
    }

    @Test
    void rejectsNonFiniteNumbersAndPartialConversions() {
        assertTrue(EspConfigurationReport
                .parse("v1,2,NaN,29,40,82,3500")
                .isEmpty());

        assertTrue(EspConfigurationReport
                .parse("v1,2,15,Infinity,40,82,3500")
                .isEmpty());

        assertTrue(EspConfigurationReport
                .parse("v1,2,15abc,29,40,82,3500")
                .isEmpty());
    }

    @Test
    void rejectsUnsupportedProtocol() {
        assertTrue(EspConfigurationReport
                .parse("v2,2,15,29,40,82,3500")
                .isEmpty());
    }

    @Test
    void validatesRevisionBoundaries() {
        assertTrue(EspConfigurationReport
                .parse("v1,4294967295,15,29,40,82,3500")
                .isPresent());

        assertTrue(EspConfigurationReport
                .parse("v1,4294967296,15,29,40,82,3500")
                .isEmpty());
    }

    @Test
    void rejectsLuminosityOutsideAdcRange() {
        assertTrue(EspConfigurationReport
                .parse("v1,2,15,29,40,82,4096")
                .isEmpty());

        assertTrue(EspConfigurationReport
                .parse("v1,2,15,29,40,82,-1")
                .isEmpty());
    }

    @Test
    void rejectsNullNonTextAndOversizedMessages() {
        assertTrue(EspConfigurationReport.parse(null).isEmpty());
        assertTrue(EspConfigurationReport.parse(123).isEmpty());
        assertTrue(EspConfigurationReport
                .parse("x".repeat(193))
                .isEmpty());
    }

    private void assertDecimal(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
