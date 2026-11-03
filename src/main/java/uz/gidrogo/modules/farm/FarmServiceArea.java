package uz.gidrogo.modules.farm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import uz.gidrogo.common.ApiException;
import uz.gidrogo.common.GeoUtils;
import uz.gidrogo.modules.product.ServiceRegion;
import uz.gidrogo.modules.product.ServiceRegionRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Band 23: ferma nuqtaga xizmat qiladimi — yagona manba.
 * 1) Fermaning faol ServiceRegion poligonlaridan biri nuqtani o'z ichiga olsa — xizmat qiladi.
 * 2) Faol poligoni yo'q bo'lsa — ferma koordinatasidan app.farm.service-radius-km ichida bo'lsa.
 * 3) Na poligon, na koordinata — hech qayerga xizmat qilmaydi.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FarmServiceArea {

    private final ServiceRegionRepository serviceRegionRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.farm.service-radius-km:30}")
    private double serviceRadiusKm;

    /** Ferma koordinatasigacha masofa (km, 0.1 aniqlikda); koordinata bo'lmasa null */
    public Double distanceKm(Farm farm, double lat, double lon) {
        if (farm.getLatitude() == null || farm.getLongitude() == null) return null;
        double meters = GeoUtils.calculateDistanceMeters(lat, lon,
                farm.getLatitude().doubleValue(), farm.getLongitude().doubleValue());
        return Math.round(meters / 100.0) / 10.0;
    }

    public boolean serves(Farm farm, double lat, double lon) {
        List<List<double[]>> polygons = activePolygons(farm.getId());
        if (!polygons.isEmpty()) {
            return polygons.stream().anyMatch(ring -> contains(ring, lat, lon));
        }
        Double distance = distanceKm(farm, lat, lon);
        return distance != null && distance <= serviceRadiusKm;
    }

    /** Server himoyasi: ro'yxatdan o'tish, manzil, buyurtma va ferma almashtirishda */
    public void requireServes(Farm farm, BigDecimal lat, BigDecimal lon) {
        if (lat == null || lon == null || (lat.signum() == 0 && lon.signum() == 0)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "LOCATION_REQUIRED",
                    "Manzil koordinatasi (latitude, longitude) kiritilishi shart");
        }
        if (!serves(farm, lat.doubleValue(), lon.doubleValue())) {
            throw ApiException.conflict("FARM_NOT_SERVING_ADDRESS",
                    farm.getName() + " ushbu manzilga xizmat ko'rsatmaydi. Iltimos, boshqa fermani tanlang");
        }
    }

    /** Manager kiritgan polygonJson to'g'ri ekanini tekshiradi (kamida 3 nuqta) */
    public void validatePolygonJson(String polygonJson) {
        if (parseRings(polygonJson).isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_POLYGON",
                    "polygonJson noto'g'ri: GeoJSON Polygon/MultiPolygon yoki [[lat, lon], ...] (kamida 3 nuqta) bo'lishi kerak");
        }
    }

    private List<List<double[]>> activePolygons(Long farmId) {
        List<List<double[]>> rings = new ArrayList<>();
        for (ServiceRegion region : serviceRegionRepository.findAllByFarmIdAndActiveTrue(farmId)) {
            List<List<double[]>> parsed = parseRings(region.getPolygonJson());
            if (parsed.isEmpty()) {
                log.warn("ServiceRegion {} (farm {}) polygonJson o'qilmadi, e'tiborsiz qoldirildi", region.getId(), farmId);
            }
            rings.addAll(parsed);
        }
        return rings;
    }

    /**
     * Qabul qilinadigan formatlar (har bir nuqta {lat, lon} ga keltiriladi):
     * GeoJSON Polygon / MultiPolygon / Feature — koordinata tartibi [lon, lat];
     * oddiy massiv [[lat, lon], ...] (V1 sxemasidagi asl format).
     */
    List<List<double[]>> parseRings(String json) {
        List<List<double[]>> rings = new ArrayList<>();
        if (json == null || json.isBlank()) return rings;
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node.has("geometry")) node = node.get("geometry");
            if (node.isObject() && node.has("coordinates")) {
                String type = node.path("type").asText("");
                JsonNode coords = node.get("coordinates");
                if ("Polygon".equalsIgnoreCase(type)) {
                    addRing(rings, coords.path(0), true);
                } else if ("MultiPolygon".equalsIgnoreCase(type)) {
                    for (JsonNode polygon : coords) addRing(rings, polygon.path(0), true);
                }
            } else if (node.isArray()) {
                addRing(rings, node, false);
            }
        } catch (Exception e) {
            return new ArrayList<>();
        }
        return rings;
    }

    private void addRing(List<List<double[]>> rings, JsonNode points, boolean lonFirst) {
        List<double[]> ring = new ArrayList<>();
        for (JsonNode p : points) {
            if (!p.isArray() || p.size() < 2 || !p.get(0).isNumber() || !p.get(1).isNumber()) return;
            double a = p.get(0).asDouble();
            double b = p.get(1).asDouble();
            ring.add(lonFirst ? new double[]{b, a} : new double[]{a, b});
        }
        if (ring.size() >= 3) rings.add(ring);
    }

    /** Ray casting: nuqta {lat, lon} ko'pburchak ichidami */
    private boolean contains(List<double[]> ring, double lat, double lon) {
        boolean inside = false;
        for (int i = 0, j = ring.size() - 1; i < ring.size(); j = i++) {
            double latI = ring.get(i)[0], lonI = ring.get(i)[1];
            double latJ = ring.get(j)[0], lonJ = ring.get(j)[1];
            if ((latI > lat) != (latJ > lat)
                    && lon < (lonJ - lonI) * (lat - latI) / (latJ - latI) + lonI) {
                inside = !inside;
            }
        }
        return inside;
    }
}
