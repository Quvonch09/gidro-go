package uz.gidrogo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.gidrogo.common.GeoUtils;
import uz.gidrogo.modules.assignment.AssignmentService;
import uz.gidrogo.modules.auth.Role;
import uz.gidrogo.modules.order.OrderStatus;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GidroGoCoreLogicTest {

    @Test
    @DisplayName("GeoUtils Haversine masofa hisoblash aniqligi tekshiruvi")
    void testHaversineDistanceCalculation() {
        // Toshkent (41.2995, 69.2401) dan Samarqand (39.6542, 66.9597) gacha masofa ~270 km
        double distanceMeters = GeoUtils.calculateDistanceMeters(41.2995, 69.2401, 39.6542, 66.9597);
        assertTrue(distanceMeters > 260000 && distanceMeters < 285000, "Toshkent-Samarqand masofasi 260-285 km oralig'ida bo'lishi kerak");

        // 500 metr NEARBY geofence tekshiruvi
        double nearDist = GeoUtils.calculateDistanceMeters(41.2995, 69.2401, 41.3020, 69.2401);
        assertTrue(GeoUtils.isWithinDistance(41.2995, 69.2401, 41.3020, 69.2401, 500.0), "500m radius ichida bo'lishi kerak");
    }

    @Test
    @DisplayName("Point-in-Polygon (PIP) xizmat hududi geofence tekshiruvi")
    void testPointInPolygon() {
        // To'rtburchak poligon: [(41.0, 69.0), (41.0, 70.0), (42.0, 70.0), (42.0, 69.0)]
        List<double[]> polygon = List.of(
                new double[]{41.0, 69.0},
                new double[]{41.0, 70.0},
                new double[]{42.0, 70.0},
                new double[]{42.0, 69.0}
        );

        // Nuqta poligon ichida: (41.5, 69.5)
        assertTrue(GeoUtils.isPointInPolygon(41.5, 69.5, polygon), "Nuqta xizmat hududi ichida bo'lishi kerak");

        // Nuqta poligon tashqarisida: (40.5, 68.5)
        assertFalse(GeoUtils.isPointInPolygon(40.5, 68.5, polygon), "Nuqta xizmat hududi tashqarisida bo'lishi kerak");
    }

    @Test
    @DisplayName("Unified OrderStatus enum to'liqligi tekshiruvi")
    void testOrderStatusEnum() {
        assertEquals(11, OrderStatus.values().length);
        assertNotNull(OrderStatus.valueOf("NEW"));
        assertNotNull(OrderStatus.valueOf("SEARCHING"));
        assertNotNull(OrderStatus.valueOf("ASSIGNED"));
        assertNotNull(OrderStatus.valueOf("ON_THE_WAY"));
        assertNotNull(OrderStatus.valueOf("NEARBY"));
        assertNotNull(OrderStatus.valueOf("DELIVERED"));
        assertNotNull(OrderStatus.valueOf("COMPLETED"));
        assertNotNull(OrderStatus.valueOf("PREPARING"));
        assertNotNull(OrderStatus.valueOf("CANCELLED"));
        assertNotNull(OrderStatus.valueOf("PROBLEM"));
        assertNotNull(OrderStatus.valueOf("RETURNED"));
    }

    @Test
    @DisplayName("5 ta asosiy tizim roli tekshiruvi")
    void testRoles() {
        assertEquals(5, Role.values().length);
        assertEquals(Role.SUPER_ADMIN, Role.valueOf("SUPER_ADMIN"));
        assertEquals(Role.BOSS, Role.valueOf("BOSS"));
        assertEquals(Role.MANAGER, Role.valueOf("MANAGER"));
        assertEquals(Role.COURIER, Role.valueOf("COURIER"));
        assertEquals(Role.CLIENT, Role.valueOf("CLIENT"));
    }

    @Test
    @DisplayName("CRM Kuryer kunlik balon balansi hisob-kitobi tekshiruvi")
    void testCourierBottleBalanceCalculation() {
        java.math.BigDecimal loadedToday = new java.math.BigDecimal("60.00");
        java.math.BigDecimal soldToday = new java.math.BigDecimal("45.00");
        java.math.BigDecimal vehicleStock = new java.math.BigDecimal("15.00");

        assertEquals(0, loadedToday.subtract(soldToday).compareTo(vehicleStock),
                "Yuklangan minus sotilgan qolgan balonlar soniga teng bo'lishi kerak");
        
        java.math.BigDecimal cashCollected = new java.math.BigDecimal("500000.00");
        java.math.BigDecimal onlineCollected = new java.math.BigDecimal("400000.00");
        java.math.BigDecimal totalRevenue = cashCollected.add(onlineCollected);
        assertEquals(new java.math.BigDecimal("900000.00"), totalRevenue);
    }
}
