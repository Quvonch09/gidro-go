package uz.gidrogo.modules.farm;

/**
 * Bossning faollashtirish holati (TZ §7.1–7.3). Hisob holati (users.status) dan alohida.
 * NOT_REQUESTED → PENDING → APPROVED | REJECTED; REJECTED bo'lgach keyingi kirishda yangi PENDING.
 */
public enum BossActivationStatus {
    NOT_REQUESTED,
    PENDING,
    APPROVED,
    REJECTED
}
