package mx.calpulli.vigia;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import java.util.Locale;

/** Hace sonar la alarma: pitidos por el canal de ALARMA (suena aunque la tableta esté en silencio),
 *  vibración y la voz que dicta la instrucción. Enciende la pantalla y pone Vigía al frente.
 *  Sigue sonando hasta que el vigilante toca "Hecho" en la página (o 10 minutos como máximo). */
public class AlarmaServicio extends Service {
    static final String SONAR = "sonar", DETENER = "detener", CANAL = "alarmas_vigia";
    static volatile boolean sonando = false;
    static volatile String claveActual = null;

    private final Handler h = new Handler(Looper.getMainLooper());
    private ToneGenerator tono;
    private TextToSpeech tts;
    private boolean ttsListo = false;
    private PowerManager.WakeLock wl;
    private int ciclo = 0, volPrevio = -1;
    private long inicio;
    private String texto = "";

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public int onStartCommand(Intent i, int flags, int startId) {
        String acc = i != null ? i.getAction() : null;
        if (DETENER.equals(acc)) { parar(); return START_NOT_STICKY; }
        String clave = i != null ? i.getStringExtra("clave") : null;
        String t = i != null ? i.getStringExtra("texto") : null;
        if (sonando && clave != null && clave.equals(claveActual)) return START_NOT_STICKY;
        texto = t == null ? "Atención" : t;
        claveActual = clave;
        enPrimerPlano();
        if (!sonando) empezar();
        return START_NOT_STICKY;
    }

    private void enPrimerPlano() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CANAL, "Alarmas de consignas", NotificationManager.IMPORTANCE_HIGH);
            ch.setSound(null, null); ch.enableVibration(false); ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            nm.createNotificationChannel(ch);
        }
        Intent abrir = new Intent(this, MainActivity.class);
        abrir.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        abrir.putExtra("alarma", true);
        PendingIntent pi = PendingIntent.getActivity(this, 1, abrir, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CANAL) : new Notification.Builder(this);
        b.setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle("Vigía · consigna").setContentText(texto)
            .setCategory(Notification.CATEGORY_ALARM).setOngoing(true).setContentIntent(pi).setFullScreenIntent(pi, true);
        if (Build.VERSION.SDK_INT < 26) b.setPriority(Notification.PRIORITY_MAX);
        Notification n = b.build();
        if (Build.VERSION.SDK_INT >= 29) startForeground(7, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        else startForeground(7, n);
        // con permiso de "mostrar sobre otras apps" se abre directo, aunque esté en Zello
        if (Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this)) { try { startActivity(abrir); } catch (Exception e) { } }
    }

    private void empezar() {
        sonando = true; inicio = System.currentTimeMillis(); ciclo = 0;
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "vigia:alarma"); wl.acquire(11 * 60 * 1000L);
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        try { volPrevio = am.getStreamVolume(AudioManager.STREAM_ALARM); am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0); } catch (Exception e) { }
        try { tono = new ToneGenerator(AudioManager.STREAM_ALARM, 100); } catch (Exception e) { tono = null; }
        tts = new TextToSpeech(this, s -> {
            if (s == TextToSpeech.SUCCESS) {
                tts.setLanguage(new Locale("es", "MX"));
                tts.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
                tts.setSpeechRate(0.95f); ttsListo = true;
            }
        });
        h.post(paso);
    }

    private final Runnable paso = new Runnable() { @Override public void run() {
        if (!sonando) return;
        if (System.currentTimeMillis() - inicio > 10 * 60 * 1000L) { parar(); return; }
        boolean voz = ttsListo && (ciclo == 1 || ciclo % 4 == 1);
        if (voz) tts.speak("Atención. " + texto, TextToSpeech.QUEUE_FLUSH, null, "vigia");
        else if (tono != null && !(ttsListo && tts.isSpeaking())) tono.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 900);
        vibrar(VibrarPatron.ALARMA);
        ciclo++;
        h.postDelayed(this, 2500);
    } };

    void vibrar(long[] p) {
        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (v == null) return;
        if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createWaveform(p, -1)); else v.vibrate(p, -1);
    }

    private void parar() {
        sonando = false; claveActual = null;
        h.removeCallbacksAndMessages(null);
        try { if (tts != null) { tts.stop(); tts.shutdown(); } } catch (Exception e) { }
        try { if (tono != null) tono.release(); } catch (Exception e) { }
        try { if (volPrevio >= 0) ((AudioManager) getSystemService(Context.AUDIO_SERVICE)).setStreamVolume(AudioManager.STREAM_ALARM, volPrevio, 0); } catch (Exception e) { }
        try { if (wl != null && wl.isHeld()) wl.release(); } catch (Exception e) { }
        stopForeground(true); stopSelf();
    }

    @Override public void onDestroy() { if (sonando) parar(); super.onDestroy(); }
}
