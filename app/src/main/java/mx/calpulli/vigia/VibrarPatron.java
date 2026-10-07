package mx.calpulli.vigia;

final class VibrarPatron {
    static final long[] ALARMA = { 0, 400, 150, 400, 150, 700 };
    static long[] de(String csv) {
        try { String[] p = csv.split(","); long[] r = new long[p.length + 1]; r[0] = 0;
            for (int i = 0; i < p.length; i++) r[i + 1] = Math.max(0, Math.min(5000, Long.parseLong(p[i].trim()))); return r;
        } catch (Exception e) { return ALARMA; }
    }
}
