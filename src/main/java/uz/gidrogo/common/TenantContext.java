package uz.gidrogo.common;

public final class TenantContext {
    private static final ThreadLocal<Long> CURRENT_FARM_ID = new ThreadLocal<>();

    private TenantContext() {}

    public static void setFarmId(Long farmId) {
        CURRENT_FARM_ID.set(farmId);
    }

    public static Long getFarmId() {
        return CURRENT_FARM_ID.get();
    }

    public static void clear() {
        CURRENT_FARM_ID.remove();
    }
}
