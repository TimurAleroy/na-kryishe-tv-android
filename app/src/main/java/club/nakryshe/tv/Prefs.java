package club.nakryshe.tv;

import android.content.Context;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Единственная настройка приложения — ссылка комнаты из Jarvis (Сервис → ТВ-экраны → «Скопировать»). */
final class Prefs {
    private static final String FILE = "nktv";
    private static final String KEY_URL = "url";

    private Prefs() {}

    static String getUrl(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY_URL, "");
    }

    static void setUrl(Context c, String url) {
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY_URL, url).apply();
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
