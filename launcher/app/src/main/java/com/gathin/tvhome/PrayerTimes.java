package com.gathin.tvhome;

import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

final class PrayerTimes {
    private static final double LAT = 1.1301, LON = 104.0529, TZ = 7.0;
    private static final double FAJR = 20.0, ISHA = 18.0;
    private static final String[] NAME = {"SUBUH", "TERBIT", "ZUHUR", "ASAR", "MAGHRIB", "ISYA"};

    private static double rad(double d) { return Math.toRadians(d); }
    private static double deg(double r) { return Math.toDegrees(r); }
    private static double fixA(double a) { return a - 360.0 * Math.floor(a / 360.0); }
    private static double fixH(double a) { return a - 24.0 * Math.floor(a / 24.0); }

    private static double julian(int y, int m, int d) {
        if (m <= 2) { y -= 1; m += 12; }
        double a = Math.floor(y / 100.0);
        double b = 2 - a + Math.floor(a / 4.0);
        return Math.floor(365.25 * (y + 4716)) + Math.floor(30.6001 * (m + 1)) + d + b - 1524.5;
    }

    private static double[] sun(double jd) {
        double dd = jd - 2451545.0;
        double g = fixA(357.529 + 0.98560028 * dd);
        double q = fixA(280.459 + 0.98564736 * dd);
        double l = fixA(q + 1.915 * Math.sin(rad(g)) + 0.020 * Math.sin(rad(2 * g)));
        double e = 23.439 - 0.00000036 * dd;
        double ra = deg(Math.atan2(Math.cos(rad(e)) * Math.sin(rad(l)), Math.cos(rad(l)))) / 15.0;
        return new double[]{deg(Math.asin(Math.sin(rad(e)) * Math.sin(rad(l)))), q / 15.0 - fixH(ra)};
    }

    private static double mid(double jd, double t) { return fixH(12.0 - sun(jd + t)[1]); }

    private static double angleTime(double jd, double angle, double t, boolean ccw) {
        double decl = sun(jd + t)[0];
        double x = (-Math.sin(rad(angle)) - Math.sin(rad(decl)) * Math.sin(rad(LAT)))
                / (Math.cos(rad(decl)) * Math.cos(rad(LAT)));
        double h = deg(Math.acos(Math.max(-1.0, Math.min(1.0, x)))) / 15.0;
        return mid(jd, t) + (ccw ? -h : h);
    }

    private static double asrTime(double jd, double t) {
        double decl = sun(jd + t)[0];
        double angle = -deg(Math.atan(1.0 / (1.0 + Math.tan(rad(Math.abs(LAT - decl))))));
        return angleTime(jd, angle, t, false);
    }

    static double[] compute(int y, int m, int d) {
        double jd = julian(y, m, d) - LON / (15.0 * 24.0);
        double[] r = {
            angleTime(jd, FAJR, 5.0 / 24, true), angleTime(jd, 0.833, 6.0 / 24, true),
            mid(jd, 12.0 / 24), asrTime(jd, 13.0 / 24),
            angleTime(jd, 0.833, 18.0 / 24, false), angleTime(jd, ISHA, 18.0 / 24, false)};
        double[] out = new double[6];
        for (int i = 0; i < 6; i++) {
            double h = fixH(r[i] + TZ - LON / 15.0);
            out[i] = Math.round(h * 60.0 + (i == 1 ? -2.0 : 2.0));
        }
        return out;
    }

    private static String label(int i, double min) {
        int m = (int) min;
        return String.format(Locale.US, "%s %02d.%02d", NAME[i], m / 60, m % 60);
    }

    static String next() {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("Asia/Jakarta"));
        double now = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
        double[] t = compute(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
        for (int i : new int[]{0, 2, 3, 4, 5}) if (t[i] > now) return label(i, t[i]);
        c.add(Calendar.DAY_OF_MONTH, 1);
        t = compute(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
        return label(0, t[0]);
    }

    static String name(int i) { return NAME[i]; }

    static String hm(double min) {
        int m = (int) min;
        return String.format(Locale.US, "%02d.%02d", m / 60, m % 60);
    }

    static double[] today() {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("Asia/Jakarta"));
        return compute(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    static double nowMinutes() {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("Asia/Jakarta"));
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
    }

    static int nextIndex(double[] t, double now) {
        for (int i : new int[]{0, 2, 3, 4, 5}) if (t[i] > now) return i;
        return 0;
    }

    static int minutesUntil(int idx, double[] t, double now) {
        double diff = t[idx] - now;
        if (diff < 0) diff += 1440;
        return (int) diff;
    }

    public static void main(String[] a) {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("Asia/Jakarta"));
        double[] t = compute(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
        for (int i = 0; i < 6; i++) System.out.println(label(i, t[i]));
        System.out.println("Berikutnya: " + next());
    }
}
