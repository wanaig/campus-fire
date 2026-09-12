package com.campusfire.inspection;

import java.math.BigDecimal;

public final class LocationProximity {
    private static final double BASE_DISTANCE_METERS = 100;
    private static final double MAX_DISTANCE_METERS = 250;
    private static final double ACCURACY_MARGIN_METERS = 30;

    private LocationProximity() { }

    public static double distanceMeters(Number expectedLat, Number expectedLon, BigDecimal latitude, BigDecimal longitude) {
        validateCoordinates(latitude, longitude);
        double dLat = expectedLat.doubleValue() - latitude.doubleValue();
        double dLon = (expectedLon.doubleValue() - longitude.doubleValue())
                * Math.cos(Math.toRadians(latitude.doubleValue()));
        return Math.sqrt(dLat * dLat + dLon * dLon) * 111000;
    }

    public static double allowedDistanceMeters(Number expectedAccuracy, Number actualAccuracy) {
        double combinedAccuracy = accuracy(expectedAccuracy) + accuracy(actualAccuracy) + ACCURACY_MARGIN_METERS;
        return Math.max(BASE_DISTANCE_METERS, Math.min(MAX_DISTANCE_METERS, combinedAccuracy));
    }

    public static void validateCoordinates(BigDecimal latitude, BigDecimal longitude) {
        if (latitude == null || longitude == null
                || latitude.doubleValue() < -90 || latitude.doubleValue() > 90
                || longitude.doubleValue() < -180 || longitude.doubleValue() > 180
                || (latitude.signum() == 0 && longitude.signum() == 0)) {
            throw new IllegalArgumentException("定位坐标无效，请重新获取当前位置");
        }
    }

    private static double accuracy(Number value) {
        if (value == null) return 0;
        double accuracy = value.doubleValue();
        return Double.isFinite(accuracy) && accuracy > 0 ? Math.min(accuracy, MAX_DISTANCE_METERS) : 0;
    }
}
