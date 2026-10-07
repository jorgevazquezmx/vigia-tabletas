package mx.calpulli.vigia;

import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.print.PrintAttributes;
import android.print.PrintManager;
import android.speech.tts.TextToSpeech;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import java.util.Locale;

/** Funciones del teléfono que la página puede usar como window.VigiaNative.* */
public class Puente {
    private final MainActivity act;
    private TextToSpeech tts;
    private boolean ttsListo = false;
    private WebView impresion;

    Puente(MainActivity a) {
        act = a;
        tts = new TextToSpeech(a, s -> {
            if (s == TextToSpeech.SUCCESS) {
                tts.setLanguage(new Locale("es", "MX"));
                tts.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
                tts.setSpeechRate(0.95f); ttsListo = true;
            }
        });
    }

    @JavascriptInterface public String version() { return act.version(); }
    @JavascriptInterface public boolean vozLista() { return ttsListo; }

    /** Lee un texto en voz alta (motor de voz de Android, funciona sin internet). */
    @JavascriptInterface public void decir(String texto) { if (ttsListo) tts.speak(texto, TextToSpeech.QUEUE_FLUSH, null, "vigia-d"); }

    /** Programa las alarmas de las próximas horas: [{k, at, texto}] */
    @JavascriptInterface public void programar(String json) { Alarmas.programar(act, json); }

    /** Empieza a sonar ya (si no está sonando esa misma alarma). */
    @JavascriptInterface public void alarma(String clave, String texto) {
        Intent s = new Intent(act, AlarmaServicio.class); s.setAction(AlarmaServicio.SONAR);
        s.putExtra("clave", clave); s.putExtra("texto", texto);
        if (Build.VERSION.SDK_INT >= 26) act.startForegroundService(s); else act.startService(s);
    }
    @JavascriptInterface public void detener() {
        if (!AlarmaServicio.sonando) return;
        Intent s = new Intent(act, AlarmaServicio.class); s.setAction(AlarmaServicio.DETENER); act.startService(s);
    }
    @JavascriptInterface public boolean sonando() { return AlarmaServicio.sonando; }

    @JavascriptInterface public void vibrar(String csv) {
        Vibrator v = (Vibrator) act.getSystemService(Context.VIBRATOR_SERVICE); if (v == null) return;
        long[] p = VibrarPatron.de(csv);
        if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createWaveform(p, -1)); else v.vibrate(p, -1);
    }

    @JavascriptInterface public void abrirPermisos() { act.runOnUiThread(act::mostrarPermisos); }
    @JavascriptInterface public void cambiarURL() { act.runOnUiThread(act::cambiarURL); }

    /** Fija la app en pantalla (modo kiosco). Android pide confirmación la primera vez. */
    @JavascriptInterface public void kiosco(boolean on) { act.runOnUiThread(() -> { try { if (on) act.startLockTask(); else act.stopLockTask(); } catch (Exception e) { } }); }

    /** Imprime un HTML con el sistema de impresión de Android. */
    @JavascriptInterface public void imprimir(String html) {
        act.runOnUiThread(() -> {
            impresion = new WebView(act);
            impresion.setWebViewClient(new WebViewClient() { @Override public void onPageFinished(WebView v, String u) {
                PrintManager pm = (PrintManager) act.getSystemService(Context.PRINT_SERVICE);
                pm.print("Vigia", v.createPrintDocumentAdapter("Vigia"), new PrintAttributes.Builder().build());
            } });
            impresion.loadDataWithBaseURL(null, html, "text/html", "utf-8", null);
        });
    }

    void aviso(String t) { act.runOnUiThread(() -> Toast.makeText(act, t, Toast.LENGTH_LONG).show()); }
}
