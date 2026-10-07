package mx.calpulli.vigia;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import org.json.JSONArray;
import org.json.JSONObject;

/** Programa en Android las alarmas (consignas y avisos de cambio de turno) que manda la página. */
public class Alarmas {
    static final String PREFS = "vigia";
    static final int MAX = 60;

    static SharedPreferences prefs(Context c) { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    /** json = [{k:"clave", at: milisegundos, texto:"..."}] */
    static synchronized void programar(Context c, String json) {
        try {
            JSONArray nuevas = new JSONArray(json);
            cancelarTodas(c);
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            long ahora = System.currentTimeMillis();
            JSONArray guardadas = new JSONArray();
            for (int i = 0; i < nuevas.length() && guardadas.length() < MAX; i++) {
                JSONObject a = nuevas.getJSONObject(i);
                long at = a.getLong("at");
                if (at <= ahora) continue;
                int n = guardadas.length();
                PendingIntent pi = pendiente(c, n, a.optString("k"), a.optString("texto"));
                Intent abrir = new Intent(c, MainActivity.class);
                PendingIntent show = PendingIntent.getActivity(c, 9000 + n, abrir, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
                if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
                else am.setAlarmClock(new AlarmManager.AlarmClockInfo(at, show), pi);
                guardadas.put(a);
            }
            prefs(c).edit().putString("alarmas", guardadas.toString()).apply();
        } catch (Exception e) { /* formato inválido: se ignora */ }
    }

    static PendingIntent pendiente(Context c, int n, String clave, String texto) {
        Intent i = new Intent(c, AlarmaReceiver.class);
        i.putExtra("clave", clave); i.putExtra("texto", texto);
        return PendingIntent.getBroadcast(c, 1000 + n, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    static void cancelarTodas(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        for (int n = 0; n < MAX; n++) am.cancel(pendiente(c, n, "", ""));
    }

    /** Tras reiniciar la tableta: vuelve a programar lo último que mandó la página. */
    static void reprogramar(Context c) { programar(c, prefs(c).getString("alarmas", "[]")); }
}
