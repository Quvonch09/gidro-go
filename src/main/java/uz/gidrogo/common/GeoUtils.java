package uz.gidrogo.common;

import java.util.List;

public final class GeoUtils {

    private static final double EARTH_RADIUS_METERS = 6371000.0;

    private GeoUtils() {}

    /**
     * Calculates geodesic distance in meters using high-precision Haversine formula
     */
    public static double calculateDistanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS_METERS * c;
    }

    /**
     * Checks if a point is within distance meters
     */
    public static boolean isWithinDistance(double lat1, double lon1, double lat2, double lon2, double maxMeters) {
        return calculateDistanceMeters(lat1, lon1, lat2, lon2) <= maxMeters;
    }

    /**
     * Ray-casting algorithm for Point-in-Polygon (PIP) testing
     * polygon is a list of [latitude, longitude] pairs
     */
    public static boolean isPointInPolygon(double pointLat, double pointLon, List<double[]> polygon) {
        if (polygon == null || polygon.size() < 3) {
            return false;
        }

        boolean inside = false;
        int n = polygon.size();

        for (int i = 0, j = n - 1; i < n; j = i++) {
            double xi = polygon.get(i)[0], yi = polygon.get(i)[1];
            double xj = polygon.get(j)[0], yj = polygon.get(j)[1];

            boolean intersect = ((yi > pointLon) != (yj > pointLon))
                    && (pointLat < (xj - xi) * (pointLon - yi) / (yj - yi) + xi);
            if (intersect) {
                inside = !inside;
            }
        }

        return inside;
    }
}
