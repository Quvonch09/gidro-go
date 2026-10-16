package uz.gidrogo.modules.superadmin;

/** Audit Log'ga yoziladigan SuperAdmin amallari. */
public enum AuditAction {
    FARM_CREATED,
    FARM_BLOCKED,
    FARM_STATUS_CHANGED,
    BOSS_UPDATED,
    BOSS_STATUS_CHANGED,
    BOSS_DELETED,
    BOSS_REQUEST_APPROVED,
    BOSS_REQUEST_REJECTED,
    CLIENT_BLOCKED,
    CLIENT_ACTIVATED,
    CLIENT_DELETED,
    PROFILE_UPDATE,
    PASSWORD_CHANGED,
    SETTINGS_UPDATED
}
