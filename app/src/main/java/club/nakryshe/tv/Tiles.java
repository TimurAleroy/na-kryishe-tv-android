package club.nakryshe.tv;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Плитки приложений на панели внизу экрана: какие приложения на приставке можно запускать с пульта и в каком порядке.
 * Список хранится в настройках (Prefs), меняется со страницы настройки с телефона.
 */
final class Tiles {
    /** Больше плиток на панель не помещается. */
    static final int MAX = 8;

    /**
     * Стандартный набор, пока плитки не настраивали: {название приложения на приставке, вероятное имя пакета}.
     * Находится то, что установлено; чего на приставке нет, того на панели нет.
     */
    private static final String[][] DEFAULTS = {
            {"okko", "ru.more.play"},
            {"ivi", "ru.ivi.client"},
            {"youtube", "com.google.android.youtube.tv"},
    };

    static final class App {
        final String pkg;
        final String label;
        final ResolveInfo info;

        App(String pkg, String label, ResolveInfo info) {
            this.pkg = pkg;
            this.label = label;
            this.info = info;
        }
    }

    private Tiles() {}

    /** Все приложения, которые можно открыть с пульта (кроме нашего), по алфавиту. */
    static List<App> installed(Context c) {
        PackageManager pm = c.getPackageManager();
        Map<String, App> map = new LinkedHashMap<String, App>();
        String[] cats = {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER};
        for (String cat : cats) {
            Intent q = new Intent(Intent.ACTION_MAIN);
            q.addCategory(cat);
            List<ResolveInfo> found;
            try {
                found = pm.queryIntentActivities(q, 0);
            } catch (Exception e) {
                continue;
            }
            if (found == null) continue;
            for (ResolveInfo r : found) {
                if (r.activityInfo == null) continue;
                String p = r.activityInfo.packageName;
                if (p == null || p.equals(c.getPackageName()) || map.containsKey(p)) continue;
                CharSequence l = null;
                try {
                    l = r.loadLabel(pm);
                } catch (Exception ignored) {
                }
                map.put(p, new App(p, l == null ? p : l.toString(), r));
            }
        }
        List<App> out = new ArrayList<App>(map.values());
        Collections.sort(out, new Comparator<App>() {
            @Override
            public int compare(App a, App b) {
                return a.label.toLowerCase(Locale.getDefault()).compareTo(b.label.toLowerCase(Locale.getDefault()));
            }
        });
        return out;
    }

    /** Плитки, которые нужно показать сейчас, в порядке слева направо. */
    static List<App> selected(Context c) {
        List<App> all = installed(c);
        String saved = Prefs.getTiles(c);
        List<App> out = new ArrayList<App>();
        if (saved == null) {
            for (String[] d : DEFAULTS) {
                App a = findDefault(all, d[0], d[1]);
                if (a != null && !out.contains(a)) out.add(a);
            }
        } else {
            for (String p : saved.split(",")) {
                String pkg = p.trim();
                if (pkg.isEmpty()) continue;
                for (App a : all) {
                    if (a.pkg.equals(pkg)) {
                        if (!out.contains(a)) out.add(a);
                        break;
                    }
                }
            }
        }
        if (out.size() > MAX) return new ArrayList<App>(out.subList(0, MAX));
        return out;
    }

    private static App findDefault(List<App> all, String label, String pkg) {
        for (App a : all) {
            if (a.pkg.equals(pkg)) return a;
        }
        for (App a : all) {
            if (a.label.trim().equalsIgnoreCase(label)) return a;
        }
        return null;
    }

    /** Названия плиток через запятую (для подсказки на экране настройки). */
    static String names(List<App> list) {
        StringBuilder b = new StringBuilder();
        for (App a : list) {
            if (b.length() > 0) b.append(", ");
            b.append(a.label);
        }
        return b.toString();
    }

    /** Открывает приложение. Возвращает false, если не вышло (удалили, нет окна запуска). */
    static boolean launch(Context c, String pkg) {
        try {
            PackageManager pm = c.getPackageManager();
            Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (i == null) i = pm.getLaunchIntentForPackage(pkg);
            if (i == null) return false;
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            c.startActivity(i);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
