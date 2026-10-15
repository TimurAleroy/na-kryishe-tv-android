package club.nakryshe.tv;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Calendar;

/**
 * Весь экран — одна веб-страница (ТВ-экран «На Крыше»). Содержимое меняется из Jarvis и не требует обновления приложения.
 * Приложение отвечает за то, чего страница сама не умеет: полный экран, «не гасить экран», работа без сети, ночная перезагрузка.
 *
 * Как открыть настройку: кнопка «Меню» на пульте или долгое нажатие «OK».
 */
public class MainActivity extends Activity {
    private static final String OFFLINE_PAGE = "file:///android_asset/offline.html";
    private static final long RETRY_MS = 10000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private FrameLayout root;
    private WebView web;
    private String target = "";        // какую ссылку показываем сейчас
    private boolean offline = false;   // сейчас показана заставка «нет связи»
    private int lastReloadDay = -1;    // в какой день уже делали ночную перезагрузку
    private TextView hint;             // подсказка на чёрном экране, пока ссылка не задана
    private boolean setupAutoShown = false;

    private final Runnable retry = new Runnable() {
        @Override
        public void run() {
            checkReachable();
        }
    };

    private final Runnable watchdog = new Runnable() {
        @Override
        public void run() {
            // Раз в сутки около 5 утра заново открываем страницу: за недели непрерывной работы анимации накапливают память
            Calendar c = Calendar.getInstance();
            int day = c.get(Calendar.DAY_OF_YEAR) + c.get(Calendar.YEAR) * 400;
            if (c.get(Calendar.HOUR_OF_DAY) == 5 && day != lastReloadDay) {
                lastReloadDay = day;
                if (!offline && web != null) load();
            }
            handler.postDelayed(this, 60000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        setContentView(root);
        hideSystemUi();
        buildWebView();
        hint = new TextView(this);
        hint.setText("Ссылка комнаты ещё не задана.\nНажмите «Меню» на пульте или удерживайте «OK», чтобы настроить.");
        hint.setTextColor(0xFFC9A880);
        hint.setTextSize(24);
        hint.setGravity(Gravity.CENTER);
        hint.setVisibility(View.GONE);
        root.addView(hint, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        handler.postDelayed(watchdog, 60000);
    }

    private void buildWebView() {
        try {
            web = new WebView(this);
        } catch (Exception e) {
            // на некоторых приставках нет системного WebView
            TextView t = new TextView(this);
            t.setText("На приставке нет «Android System WebView».\nУстановите его из Google Play и откройте приложение снова.");
            t.setTextColor(Color.WHITE);
            t.setTextSize(24);
            t.setGravity(Gravity.CENTER);
            root.addView(t, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            return;
        }
        web.setBackgroundColor(Color.BLACK);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);   // фоновое видео должно играть само
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setSupportZoom(false);
        web.setVerticalScrollBarEnabled(false);
        web.setHorizontalScrollBarEnabled(false);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public Bitmap getDefaultVideoPoster() {
                // без этого пока видео не загрузилось, браузер рисует серую кнопку «play»
                return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
            }
        });

        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request != null && request.isForMainFrame()) showOffline();
            }

            @SuppressWarnings("deprecation")
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                // Android 5–6: старая версия обработчика
                if (Build.VERSION.SDK_INT < 23 && failingUrl != null && failingUrl.equals(target)) showOffline();
            }

            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {
                if (request != null && request.isForMainFrame() && response != null && response.getStatusCode() >= 500) showOffline();
            }

            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                // системный процесс страницы упал (нехватка памяти): пересоздаём экран, а не показываем белое окно
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        recreate();
                    }
                });
                return true;
            }
        });

        root.addView(web, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    /** Открывает сохранённую ссылку (если она задана). */
    private void load() {
        if (web == null) return;
        String url = Prefs.getUrl(this);
        if (url.isEmpty()) return;
        handler.removeCallbacks(retry);
        target = url;
        offline = false;
        web.loadUrl(url);
    }

    private void showOffline() {
        if (offline || web == null) return;
        offline = true;
        web.loadUrl(OFFLINE_PAGE);
        handler.removeCallbacks(retry);
        handler.postDelayed(retry, RETRY_MS);
    }

    /** Проверяем связь в фоне и только когда сайт отвечает, заново открываем страницу — так заставка не мигает. */
    private void checkReachable() {
        final String url = target;
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean ok = false;
                try {
                    HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                    c.setConnectTimeout(5000);
                    c.setReadTimeout(5000);
                    c.setRequestMethod("HEAD");
                    ok = c.getResponseCode() < 500;
                    c.disconnect();
                } catch (Exception ignored) {
                }
                final boolean reachable = ok;
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (!offline) return;
                        if (reachable) load();
                        else handler.postDelayed(retry, RETRY_MS);
                    }
                });
            }
        }, "nktv-reach").start();
    }

    private void openSetup() {
        startActivity(new Intent(this, SetupActivity.class));
    }

    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUi();
        if (web != null) {
            web.onResume();
            // вернулись с экрана настройки (или первый запуск): ссылка могла поменяться
            String u = Prefs.getUrl(this);
            if (u.isEmpty()) {
                hint.setVisibility(View.VISIBLE);
                if (!setupAutoShown) {   // первый запуск: сразу показываем настройку, но только один раз — чтобы из неё можно было выйти
                    setupAutoShown = true;
                    openSetup();
                }
            } else {
                hint.setVisibility(View.GONE);
                if (!u.equals(target)) load();
            }
        }
    }

    @Override
    protected void onPause() {
        if (web != null) web.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (web != null) {
            root.removeView(web);
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            openSetup();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            event.startTracking();   // долгое нажатие «OK» открывает настройку
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_BACK) return true;   // «Назад» ничего не закрывает: экран должен работать, пока кто-то не нажмёт «Домой»
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            openSetup();
            return true;
        }
        return super.onKeyLongPress(keyCode, event);
    }
}
