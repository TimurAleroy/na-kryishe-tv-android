package club.nakryshe.tv;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Экран настройки. Ссылку комнаты можно задать двумя способами:
 * 1) с телефона: открыть показанный на экране адрес и вставить ссылку (удобно — ключ длинный);
 * 2) набрать здесь пультом или клавиатурой.
 */
public class SetupActivity extends Activity {
    private static final int PORT = 8765;
    private static final int BG = 0xFF1E1004, TEXT = 0xFFF0DFC0, SUB = 0xFFC9A880, MUTED = 0xFF8A6A52, ACCENT = 0xFFB87333;

    private SetupServer server;
    private TextView statusView, addrView, autoInfo;
    private EditText input;
    private Button autoBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(40);
        col.setPadding(pad, dp(28), pad, dp(28));
        scroll.addView(col, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);

        TextView title = text("НА КРЫШЕ · ЭКРАН ДЛЯ ТВ", 15, ACCENT);
        title.setLetterSpacing(0.2f);
        col.addView(title);

        statusView = text("", 17, MUTED);
        statusView.setPadding(0, dp(8), 0, dp(20));
        col.addView(statusView);

        col.addView(text("Как задать ссылку комнаты", 20, TEXT));

        TextView s1 = text("1. На телефоне (он должен быть в той же сети Wi-Fi, что и приставка) откройте в браузере адрес:", 17, SUB);
        s1.setPadding(0, dp(10), 0, dp(6));
        col.addView(s1);

        addrView = text("", 30, ACCENT);
        addrView.setPadding(0, dp(2), 0, dp(10));
        col.addView(addrView);

        TextView s2 = text("2. Вставьте ссылку из Jarvis («Сервис → ТВ-экраны» → комната → «Скопировать») и нажмите «Сохранить» на странице.", 17, SUB);
        s2.setPadding(0, 0, 0, dp(16));
        col.addView(s2);

        TextView s3 = text("Или введите ссылку здесь — пультом или клавиатурой:", 17, SUB);
        s3.setPadding(0, 0, 0, dp(6));
        col.addView(s3);

        input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.setHint("https://…/?room=1&key=…");
        input.setHintTextColor(MUTED);
        input.setTextColor(TEXT);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        input.setPadding(dp(14), dp(10), dp(14), dp(10));
        input.setBackground(rect(0xFF20120A, 0x66B87333, 12));
        input.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    save();
                    return true;
                }
                return false;
            }
        });
        col.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(16), 0, 0);
        Button saveBtn = button("Сохранить");
        saveBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                save();
            }
        });
        Button closeBtn = button("Закрыть");
        closeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        row.addView(saveBtn, rowParams());
        row.addView(closeBtn, rowParams());
        col.addView(row);

        TextView sep = text("Автозапуск и системные настройки", 20, TEXT);
        sep.setPadding(0, dp(30), 0, dp(6));
        col.addView(sep);

        autoInfo = text("", 16, SUB);
        autoInfo.setPadding(0, 0, 0, dp(8));
        col.addView(autoInfo);

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        autoBtn = button("Разрешить автозапуск");
        autoBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                askOverlay();
            }
        });
        Button sysBtn = button("Настройки Android");
        sysBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    startActivity(new Intent(Settings.ACTION_SETTINGS));
                } catch (Exception e) {
                    Toast.makeText(SetupActivity.this, "Не удалось открыть настройки Android", Toast.LENGTH_LONG).show();
                }
            }
        });
        row2.addView(autoBtn, rowParams());
        row2.addView(sysBtn, rowParams());
        col.addView(row2);

        TextView tip = text("Открыть эту настройку снова: кнопка «Меню» на пульте или долгое нажатие «OK» на работающем экране.", 15, MUTED);
        tip.setPadding(0, dp(24), 0, 0);
        col.addView(tip);

        saveBtn.requestFocus();
    }

    @Override
    protected void onStart() {
        super.onStart();
        startServer();
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    protected void onStop() {
        if (server != null) {
            server.stop();
            server = null;
        }
        super.onStop();
    }

    private void startServer() {
        if (server != null) return;
        SetupServer s = new SetupServer(PORT, new SetupServer.Listener() {
            @Override
            public String onSubmit(String url) {
                final String clean = Prefs.normalize(url);
                String err = Prefs.check(clean);
                if (err != null) return err;
                Prefs.setUrl(SetupActivity.this, clean);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(SetupActivity.this, "Ссылка сохранена", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                });
                return null;
            }

            @Override
            public String status() {
                return describe();
            }
        });
        try {
            s.start();
            server = s;
        } catch (Exception e) {
            server = null;
        }
    }

    private void save() {
        String clean = Prefs.normalize(input.getText().toString());
        String err = Prefs.check(clean);
        if (err != null) {
            Toast.makeText(this, err, Toast.LENGTH_LONG).show();
            return;
        }
        Prefs.setUrl(this, clean);
        Toast.makeText(this, "Ссылка сохранена", Toast.LENGTH_SHORT).show();
        finish();
    }

    private String describe() {
        String room = Prefs.roomOf(Prefs.getUrl(this));
        return room.isEmpty() ? "Сейчас: ссылка ещё не задана" : "Сейчас: комната " + room + " (ссылка сохранена)";
    }

    private void refresh() {
        statusView.setText(describe());
        List<String> ips = localAddresses();
        if (server == null) {
            addrView.setText("Не удалось открыть порт " + PORT + ". Введите ссылку ниже.");
        } else if (ips.isEmpty()) {
            addrView.setText("Нет сети. Подключите Wi-Fi или кабель.");
        } else {
            StringBuilder b = new StringBuilder();
            for (String ip : ips) {
                if (b.length() > 0) b.append("\n");
                b.append("http://").append(ip).append(":").append(PORT);
            }
            addrView.setText(b.toString());
        }
        boolean can = canOverlay();
        autoInfo.setText(can
                ? "Автозапуск разрешён: после включения приставки экран откроется сам."
                : "Чтобы экран открывался сам после включения приставки, нужно разрешить «Поверх других окон». Если такого пункта нет — см. README.");
        autoBtn.setText(can ? "Автозапуск разрешён ✓" : "Разрешить автозапуск");
    }

    private boolean canOverlay() {
        return Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this);
    }

    private void askOverlay() {
        if (canOverlay()) return;
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            Toast.makeText(this, "На этой приставке нет такого экрана настроек. Подробнее — в README.", Toast.LENGTH_LONG).show();
        }
    }

    /** Адреса приставки в домашней сети (Wi-Fi или кабель). */
    private static List<String> localAddresses() {
        List<String> out = new ArrayList<String>();
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (a instanceof Inet4Address && a.isSiteLocalAddress()) out.add(a.getHostAddress());
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    // ───── мелкие помощники для интерфейса ─────

    private int dp(int v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        return t;
    }

    private GradientDrawable rect(int fill, int stroke, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radiusDp));
        d.setStroke(dp(1), stroke);
        return d;
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        b.setPadding(dp(22), dp(10), dp(22), dp(10));
        b.setGravity(Gravity.CENTER);
        // фокус пультом должен быть хорошо виден: на выбранной кнопке медная заливка
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_focused}, rect(ACCENT, ACCENT, 28));
        bg.addState(new int[]{android.R.attr.state_pressed}, rect(0xFF8F5A28, 0xFF8F5A28, 28));
        bg.addState(new int[]{}, rect(0xFF2A160F, 0x88B87333, 28));
        b.setBackground(bg);
        b.setTextColor(new ColorStateList(
                new int[][]{{android.R.attr.state_focused}, {android.R.attr.state_pressed}, {}},
                new int[]{BG, BG, TEXT}));
        b.setFocusable(true);
        return b;
    }

    private LinearLayout.LayoutParams rowParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.rightMargin = dp(14);
        return p;
    }
}
