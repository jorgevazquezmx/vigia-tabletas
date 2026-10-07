package mx.calpulli.vigia;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;

/** Al prender la tableta: reprograma las alarmas y abre Vigía. */
public class ArranqueReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        Alarmas.reprogramar(c);
        if (Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(c)) {
            Intent a = new Intent(c, MainActivity.class);
            a.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try { c.startActivity(a); } catch (Exception e) { }
        }
    }
}
