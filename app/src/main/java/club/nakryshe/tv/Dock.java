package club.nakryshe.tv;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/**
 * Панель плиток внизу по центру. Появляется по любой кнопке пульта (стрелки, «OK»), стрелки влево/вправо переключают плитки,
 * «OK» открывает приложение, «Назад» или стрелка вверх убирают панель. Если ничего не нажимать, панель сама уходит через 15 секунд.
 *
 * Панель маленькая и стоит внизу по центру, у самого края безопасной зоны экрана: она занимает как можно меньше места и почти не закрывает
 * надписи. Название приложения показывается только над выбранной плиткой.
 * Плитки — обычные кнопки Android: пульт ведёт себя привычно и ничего не зависит от страницы ТВ.
 */
final class Dock {
    private static final long AUTO_HIDE_MS = 15000;
    private static final int TEXT = 0xFFF0DFC0, SUB = 0xFFC9A880, ACCENT = 0xFFB87333;

    private final Activity act;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final LinearLayout panel;
    private boolean open = false;

    private final Runnable autoHide = new Runnable() {
        @Override
        public void run() {
            hide();
        }
    };

    Dock(Activity act, FrameLayout parent) {
        this.act = act;
        // подпись над выбранной плиткой выходит за границы панели, поэтому обрезку у родителей выключаем
        parent.setClipChildren(false);
        panel = new LinearLayout(act);
        panel.setOrientation(LinearLayout.HORIZONTAL);
        panel.setGravity(Gravity.CENTER);
        panel.setPadding(dp(4), dp(4), dp(4), dp(4));
        panel.setBackground(rect(0xEB1E1004, 0x66B87333, 22, 1));
        panel.setClipChildren(false);
        panel.setClipToPadding(false);
        panel.setVisibility(View.GONE);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        lp.bottomMargin = dp(24);   // у самого края безопасной зоны (поле страницы ТВ — 5% высоты)
        parent.addView(panel, lp);
    }

    boolean isOpen() {
        return open;
    }

    /** Показывает панель. Возвращает false, если плиток нет (тогда кнопки пульта работают как раньше). */
    boolean show() {
        List<Tiles.App> list = Tiles.selected(act);
        if (list.isEmpty()) return false;
        panel.removeAllViews();
        PackageManager pm = act.getPackageManager();
        View first = null;
        for (Tiles.App a : list) {
            View t = tile(a, pm);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(52), dp(52));
            lp.leftMargin = dp(4);
            lp.rightMargin = dp(4);
            panel.addView(t, lp);
            if (first == null) first = t;
        }
        panel.setVisibility(View.VISIBLE);
        panel.setAlpha(0f);
        panel.animate().alpha(1f).setDuration(180).start();
        open = true;
        if (first != null) first.requestFocus();
        poke();
        return true;
    }

    void hide() {
        handler.removeCallbacks(autoHide);
        open = false;
        panel.animate().cancel();
        panel.setVisibility(View.GONE);
        panel.removeAllViews();
    }

    /** Любое нажатие откладывает автоматическое скрытие. */
    void poke() {
        handler.removeCallbacks(autoHide);
        handler.postDelayed(autoHide, AUTO_HIDE_MS);
    }

    private View tile(final Tiles.App app, PackageManager pm) {
        FrameLayout t = new FrameLayout(act);
        t.setClipChildren(false);
        t.setFocusable(true);
        t.setFocusableInTouchMode(true);
        t.setClickable(true);
        // выбранная плитка обводится медной рамкой, остальные без фона
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_focused}, rect(0x40B87333, ACCENT, 14, 2));
        bg.addState(new int[]{android.R.attr.state_pressed}, rect(0x66B87333, ACCENT, 14, 2));
        bg.addState(new int[]{}, rect(0x00000000, 0x00000000, 14, 0));
        t.setBackground(bg);

        ImageView icon = new ImageView(act);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        Drawable d = null;
        try {
            d = app.info.loadIcon(pm);
        } catch (Exception ignored) {
        }
        if (d != null) icon.setImageDrawable(d);
        t.addView(icon, new FrameLayout.LayoutParams(dp(38), dp(38), Gravity.CENTER));

        // название приложения: маленькая подпись над панелью, видна только у выбранной плитки
        final TextView label = new TextView(act);
        label.setText(app.label);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        label.setTextColor(TEXT);
        label.setGravity(Gravity.CENTER);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setMaxWidth(dp(150));
        label.setPadding(dp(10), dp(4), dp(10), dp(4));
        label.setBackground(rect(0xF21E1004, 0x99B87333, 10, 1));
        label.setVisibility(View.INVISIBLE);
        FrameLayout.LayoutParams llp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        llp.topMargin = -dp(38);
        t.addView(label, llp);

        t.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                label.setVisibility(hasFocus ? View.VISIBLE : View.INVISIBLE);
                v.animate().scaleX(hasFocus ? 1.1f : 1f).scaleY(hasFocus ? 1.1f : 1f).setDuration(120).start();
                if (hasFocus) poke();
            }
        });
        t.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hide();
                if (!Tiles.launch(act, app.pkg)) {
                    Toast.makeText(act, "Не удалось открыть «" + app.label + "»", Toast.LENGTH_LONG).show();
                }
            }
        });
        return t;
    }

    private int dp(int v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, act.getResources().getDisplayMetrics()));
    }

    private GradientDrawable rect(int fill, int stroke, int radiusDp, int strokeDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) d.setStroke(dp(strokeDp), stroke);
        return d;
    }
}
