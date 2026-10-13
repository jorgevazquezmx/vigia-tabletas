package mx.calpulli.vigia;

import android.content.Context;
import android.media.AudioManager;

/** Tabletas de caseta: el volumen siempre al máximo (alarmas, voz, avisos, timbre y llamadas). */
public class Volumen {
    private static final int[] CANALES = { AudioManager.STREAM_ALARM, AudioManager.STREAM_MUSIC,
        AudioManager.STREAM_NOTIFICATION, AudioManager.STREAM_RING, AudioManager.STREAM_VOICE_CALL };

    static void alMaximo(Context c) {
        try {
            AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) return;
            try { if (am.getRingerMode() != AudioManager.RINGER_MODE_NORMAL) am.setRingerMode(AudioManager.RINGER_MODE_NORMAL); } catch (Exception e) { }
            for (int s : CANALES) {
                try { int max = am.getStreamMaxVolume(s); if (am.getStreamVolume(s) < max) am.setStreamVolume(s, max, 0); } catch (Exception e) { }
            }
        } catch (Exception e) { }
    }
}
