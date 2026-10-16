package uz.gidrogo.common;

import java.util.regex.Pattern;

/**
 * Fayl URL'lari (BE-13 / BUG-012): API javobida nisbiy yo'l qaytariladi (/api/files/view/...),
 * shunda HTTPS sahifada "mixed content" bloklanmaydi. Bazadagi eski mutlaq URL'lar V14 da tuzatilgan;
 * bu yerda — javob qatlamidagi qo'shimcha himoya.
 */
public final class FileUrls {

    private static final Pattern ABSOLUTE_FILE_URL = Pattern.compile("^https?://[^/]+(/api/files/view/.*)$");
    private static final String DEAD_ASSETS_PREFIX = "https://gidrogo.uz/assets/";

    private FileUrls() {}

    public static String normalize(String url) {
        if (url == null || url.isBlank()) return null;
        String u = url.trim();
        if (u.startsWith(DEAD_ASSETS_PREFIX)) return null;
        var m = ABSOLUTE_FILE_URL.matcher(u);
        return m.matches() ? m.group(1) : u;
    }
}
