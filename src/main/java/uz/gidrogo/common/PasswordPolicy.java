package uz.gidrogo.common;

/**
 * Yagona parol siyosati (BE-21 / SEC-05): ferma yaratish, boss tahriri va parol almashtirishda bir xil.
 * 8–50 belgi, kamida bitta harf va bitta raqam. Buzilsa → 422 PASSWORD_POLICY_VIOLATION.
 */
public final class PasswordPolicy {

    public static final int MIN = 8;
    public static final int MAX = 50;
    public static final String DESCRIPTION =
            "Parol 8–50 belgidan iborat bo'lishi va kamida bitta harf hamda bitta raqam bo'lishi kerak";

    private PasswordPolicy() {}

    public static boolean isValid(String password) {
        if (password == null) return false;
        int len = password.length();
        if (len < MIN || len > MAX) return false;
        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        return hasLetter && hasDigit;
    }

    public static void validate(String password, String field) {
        if (!isValid(password)) {
            throw ApiException.unprocessable("PASSWORD_POLICY_VIOLATION", field, DESCRIPTION);
        }
    }
}
