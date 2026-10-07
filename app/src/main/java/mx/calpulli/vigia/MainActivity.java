package mx.calpulli.vigia;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.view.WindowManager;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;

/** Vigía para tabletas de la empresa: abre la MISMA página de Netlify (mismos datos que el celular
 *  o el navegador) y le agrega alarmas nativas, voz, vibración y pantalla siempre encendida. */
public class MainActivity extends Activity {
    static final String URL_DEF = "https://pixki.netlify.app/";
    WebView web;
    Puente puente;
    private ValueCallback<Uri[]> archivoCb;
    private PermissionRequest permPendiente;

    static String url(Context c) { return Alarmas.prefs(c).getString("url", URL_DEF); }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true); }
        web = new WebView(this);
        web.setBackgroundColor(Color.WHITE);
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setGeolocationEnabled(true);
        s.setAllowFileAccess(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setUserAgentString(s.getUserAgentString() + " VigiaAndroid/" + version());
        puente = new Puente(this);
        web.addJavascriptInterface(puente, "VigiaNative");
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) { return abrirFuera(r.getUrl()); }
            @Override public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
                if (r.isForMainFrame()) v.loadDataWithBaseURL(null, paginaSinRed(), "text/html", "utf-8", null);
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onPermissionRequest(PermissionRequest r) {
                runOnUiThread(() -> {
                    if (tienePermiso(Manifest.permission.CAMERA) && tienePermiso(Manifest.permission.RECORD_AUDIO)) r.grant(r.getResources());
                    else { permPendiente = r; pedirPermisos(); }
                });
            }
            @Override public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback cb) { cb.invoke(origin, true, true); }
            @Override public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                archivoCb = cb;
                try { startActivityForResult(p.createIntent(), 77); } catch (Exception e) { archivoCb = null; return false; }
                return true;
            }
        });
        pedirPermisos();
        if (Alarmas.prefs(this).getBoolean("primera", true)) {
            Alarmas.prefs(this).edit().putBoolean("primera", false).apply();
            web.loadUrl(url(this));
            web.postDelayed(this::mostrarPermisos, 2500);
        } else web.loadUrl(url(this));
    }

    String version() { try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception e) { return "1"; } }

    boolean abrirFuera(Uri u) {
        String sch = u.getScheme() == null ? "" : u.getScheme();
        String base = Uri.parse(url(this)).getHost();
        if (("https".equals(sch) || "http".equals(sch)) && base != null && base.equals(u.getHost())) return false;
        if ("https".equals(sch) && u.getHost() != null && u.getHost().endsWith("supabase.co")) return false;
        try {
            Intent i = "intent".equals(sch) ? Intent.parseUri(u.toString(), Intent.URI_INTENT_SCHEME) : new Intent(Intent.ACTION_VIEW, u);
            startActivity(i);
        } catch (Exception e) {
            if ("intent".equals(sch)) puente.aviso("Falta instalar la app de la impresora (RawBT).");
        }
        return true;
    }

    String paginaSinRed() {
        return "<html><body style='font-family:sans-serif;padding:30px;text-align:center;color:#10212f'><h2>Sin conexión</h2>"
            + "<p>No se pudo abrir Vigía. Revisa el internet de la tableta.</p>"
            + "<p><button style='font-size:20px;padding:14px 24px' onclick='location.href=\"" + url(this) + "\"'>Reintentar</button></p>"
            + "<p><button style='font-size:16px;padding:10px 18px' onclick='VigiaNative.cambiarURL()'>Cambiar dirección</button></p></body></html>";
    }

    boolean tienePermiso(String p) { return Build.VERSION.SDK_INT < 23 || checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED; }

    void pedirPermisos() {
        if (Build.VERSION.SDK_INT < 23) return;
        ArrayList<String> l = new ArrayList<>();
        for (String p : new String[]{ Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO, Manifest.permission.ACCESS_FINE_LOCATION })
            if (!tienePermiso(p)) l.add(p);
        if (Build.VERSION.SDK_INT >= 33 && !tienePermiso(Manifest.permission.POST_NOTIFICATIONS)) l.add(Manifest.permission.POST_NOTIFICATIONS);
        if (!l.isEmpty()) requestPermissions(l.toArray(new String[0]), 5);
    }

    @Override public void onRequestPermissionsResult(int rc, String[] p, int[] g) {
        if (permPendiente != null) {
            if (tienePermiso(Manifest.permission.CAMERA)) permPendiente.grant(permPendiente.getResources()); else permPendiente.deny();
            permPendiente = null;
        }
    }

    @Override protected void onActivityResult(int rc, int res, Intent d) {
        if (rc == 77 && archivoCb != null) { archivoCb.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res, d)); archivoCb = null; }
    }

    /** Lista de ajustes de Android que necesita la tableta para que las alarmas no fallen. */
    void mostrarPermisos() {
        LinearLayout ll = new LinearLayout(this); ll.setOrientation(LinearLayout.VERTICAL); ll.setPadding(40, 20, 40, 10);
        TextView t = new TextView(this); t.setTextSize(15);
        t.setText("Para que las alarmas suenen siempre (pantalla apagada, Zello abierto o tableta en silencio), activa lo que tenga ✗:");
        ll.addView(t);
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        boolean overlay = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this);
        boolean bateria = Build.VERSION.SDK_INT < 23 || pm.isIgnoringBatteryOptimizations(getPackageName());
        boolean notif = Build.VERSION.SDK_INT < 24 || nm.areNotificationsEnabled();
        boolean full = Build.VERSION.SDK_INT < 34 || nm.canUseFullScreenIntent();
        agregar(ll, overlay, "Mostrar sobre otras apps", Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
        agregar(ll, bateria, "Sin restricción de batería", Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
        agregar(ll, notif, "Notificaciones", Build.VERSION.SDK_INT >= 26 ? Settings.ACTION_APP_NOTIFICATION_SETTINGS : Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        agregar(ll, full, "Alarmas en pantalla completa", Build.VERSION.SDK_INT >= 34 ? Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT : null);
        new AlertDialog.Builder(this).setTitle("Ajustes de la tableta").setView(ll).setPositiveButton("Listo", null).show();
    }

    private void agregar(LinearLayout ll, boolean ok, String nombre, String accion) {
        TextView b = new TextView(this);
        b.setTextSize(18); b.setPadding(0, 26, 0, 26);
        b.setText((ok ? "✓  " : "✗  ") + nombre + (ok ? "" : "  → tocar para activar"));
        b.setTextColor(ok ? Color.rgb(15, 138, 75) : Color.rgb(198, 40, 40));
        if (!ok && accion != null) b.setOnClickListener(v -> {
            try {
                Intent i = new Intent(accion);
                if (Settings.ACTION_APP_NOTIFICATION_SETTINGS.equals(accion)) i.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
                else i.setData(Uri.parse("package:" + getPackageName()));
                startActivity(i);
            } catch (Exception e) { startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))); }
        });
        ll.addView(b);
    }

    void cambiarURL() {
        EditText e = new EditText(this); e.setText(url(this)); e.setSingleLine(true);
        new AlertDialog.Builder(this).setTitle("Dirección de Vigía").setView(e)
            .setPositiveButton("Guardar", (d, w) -> {
                String u = e.getText().toString().trim();
                if (!u.startsWith("https://")) u = "https://" + u;
                if (!u.endsWith("/") && !u.contains(".html")) u = u + "/";
                Alarmas.prefs(this).edit().putString("url", u).apply();
                web.loadUrl(u);
            }).setNegativeButton("Cancelar", null).show();
    }

    @Override protected void onNewIntent(Intent i) { super.onNewIntent(i); setIntent(i); }
    @Override protected void onResume() { super.onResume(); web.onResume(); web.evaluateJavascript("window.dispatchEvent(new Event('vigia-nativo'))", null); }
    @Override protected void onPause() { super.onPause(); }
    @Override public void onBackPressed() { if (web.canGoBack()) web.goBack(); }
}
