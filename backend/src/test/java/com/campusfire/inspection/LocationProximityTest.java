package com.campusfire.inspection;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LocationProximityTest {
    @Test
    void shouldMeasureShortDistanceInMeters() {
        double distance = LocationProximity.distanceMeters(
                new BigDecimal("28.2277800"), new BigDecimal("112.9388600"),
                new BigDecimal("28.2287800"), new BigDecimal("112.9388600"));

        assertEquals(111, distance, 0.5);
    }

    @Test
    void shouldUseReportedAccuracyWithinSafeLimits() {
        assertEquals(100, LocationProximity.allowedDistanceMeters(null, null), 0.01);
        assertEquals(160, LocationProximity.allowedDistanceMeters(60, 70), 0.01);
        assertEquals(250, LocationProximity.allowedDistanceMeters(300, 300), 0.01);
    }

    @Test
    void shouldRejectMissingZeroCoordinateFallback() {
        assertThrows(IllegalArgumentException.class, () -> LocationProximity.validateCoordinates(
                BigDecimal.ZERO, BigDecimal.ZERO));
    }
}
