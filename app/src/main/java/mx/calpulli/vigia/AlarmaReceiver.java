package mx.calpulli.vigia;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class AlarmaReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        Intent s = new Intent(c, AlarmaServicio.class);
        s.setAction(AlarmaServicio.SONAR);
        s.putExtra("clave", i.getStringExtra("clave"));
        s.putExtra("texto", i.getStringExtra("texto"));
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(s); else c.startService(s);
    }
}
