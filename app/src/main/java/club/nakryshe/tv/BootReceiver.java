package club.nakryshe.tv;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** После включения приставки (и после обновления приложения) сразу открывает экран. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String a = intent == null ? null : intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(a)
                || "android.intent.action.QUICKBOOT_POWERON".equals(a)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)) {
            Intent start = new Intent(context, MainActivity.class);
            start.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                context.startActivity(start);
            } catch (Exception ignored) {
                // Android 10+ без разрешения «поверх других окон» не даёт запуститься из фона — это нормально
            }
        }
    }
}
