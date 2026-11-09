package club.nakryshe.tv;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Настройки приложения: ссылка комнаты из Jarvis (Сервис → ТВ-экраны → «Скопировать») и список плиток приложений.
 */
final class Prefs {
    private static final String FILE = "nktv";
    private static final String KEY_URL = "url";
    private static final String KEY_TILES = "tiles";

    private Prefs() {}

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    static String getUrl(Context c) {
        return sp(c).getString(KEY_URL, "");
    }

    static void setUrl(Context c, String url) {
        sp(c).edit().putString(KEY_URL, url).apply();
    }

    /**
     * Плитки приложений: имена пакетов по порядку через запятую.
     * null — не настраивалось (показываем стандартный набор), пустая строка — плиток нет совсем.
     */
    static String getTiles(Context c) {
        SharedPreferences p = sp(c);
        return p.contains(KEY_TILES) ? p.getString(KEY_TILES, "") : null;
    }

    static void setTiles(Context c, String csv) {
        sp(c).edit().putString(KEY_TILES, csv == null ? "" : csv).apply();
    }

    /** Вернуть стандартный набор плиток. */
    static void resetTiles(Context c) {
        sp(c).edit().remove(KEY_TILES).apply();
    }

    /**
     * Проверяет введённую ссылку. Возвращает null, если всё хорошо, иначе — понятное сообщение.
     * Ссылка комнаты выглядит так: https://…/?room=1&key=…
     */
    static String check(String raw) {
        String u = normalize(raw);
        if (u.isEmpty()) return "Ссылка пустая. Вставьте ссылку комнаты из Jarvis.";
        if (!(u.startsWith("https://") || u.startsWith("http://"))) return "Ссылка должна начинаться с https://";
        if (!u.contains("room=") || !u.contains("key=")) {
            return "Это не ссылка комнаты: в ней нет room= и key=. В Jarvis откройте «Сервис → ТВ-экраны», выберите комнату и нажмите «Скопировать».";
        }
        return null;
    }

    /** Убирает пробелы и переносы строк, которые легко прихватить при копировании. */
    static String normalize(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("\\s+", "");
    }

    /** Номер комнаты из ссылки (для подсказки на экране настройки) или пустая строка. */
    static String roomOf(String url) {
        if (url == null) return "";
        Matcher m = Pattern.compile("[?&]room=([^&]+)").matcher(url);
        return m.find() ? m.group(1) : "";
    }
}
