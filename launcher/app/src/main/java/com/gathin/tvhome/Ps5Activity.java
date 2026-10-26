package com.gathin.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.io.File;
import java.util.*;

/** PS5-style home: app row on top, big name, Open button, clock + prayer times bottom right. */
public class Ps5Activity extends Activity {
    private static final String[] FIRST = {"com.google.android.youtube.tv", "com.netflix.ninja",
            "com.adixtream.app", "com.android.vending"};
    private final List<ResolveInfo> apps = new ArrayList<>();
    private final Map<String, Bitmap> cache = new HashMap<>();
    private PackageManager pm;
    private float d;
    private ImageView bg;
    private TextView title, sub, openBtn, pNext;
    private final TextView[] pTime = new TextView[6];
    private final LinearLayout[] pCol = new LinearLayout[6];
    private RecyclerView row;
    private int selected = -1, lit = -1;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable tick = this::refreshPrayer;

    private int dp(float v) { return (int) (v * d + 0.5f); }

    private GradientDrawable round(int color, float r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(r * d);
        return g;
    }

    private GradientDrawable ring(float r) {
        GradientDrawable g = round(0x00000000, r);
        g.setStroke(dp(3), Color.WHITE);
        return g;
    }

    private TextView text(float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setSingleLine(true);
        if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }

    private static int rank(String p) {
        for (int i = 0; i < FIRST.length; i++) if (FIRST[i].equals(p)) return i;
        return 100;
    }

    private void loadApps() {
        Set<String> seen = new HashSet<>();
        for (String cat : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            Intent i = new Intent(Intent.ACTION_MAIN).addCategory(cat);
            for (ResolveInfo r : pm.queryIntentActivities(i, 0)) {
                String p = r.activityInfo.packageName;
                if (p.equals(getPackageName()) || !seen.add(p)) continue;
                apps.add(r);
            }
        }
        Collections.sort(apps, (a, b) -> {
            int x = rank(a.activityInfo.packageName), y = rank(b.activityInfo.packageName);
            if (x != y) return x - y;
            return String.valueOf(a.loadLabel(pm)).compareToIgnoreCase(String.valueOf(b.loadLabel(pm)));
        });
    }

    private void open(int pos) {
        if (pos < 0 || pos >= apps.size()) return;
        ResolveInfo r = apps.get(pos);
        Intent i = new Intent(Intent.ACTION_MAIN)
                .setClassName(r.activityInfo.packageName, r.activityInfo.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { startActivity(i); } catch (RuntimeException ignored) { }
    }

    private Bitmap decode(File f) {
        try {
            if (!f.isFile()) return null;
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getPath(), o);
            int s = 1;
            while (o.outWidth / (s * 2) >= 1280) s *= 2;
            o = new BitmapFactory.Options();
            o.inSampleSize = s;
            return BitmapFactory.decodeFile(f.getPath(), o);
        } catch (RuntimeException | OutOfMemoryError e) {
            return null;
        }
    }

    private Bitmap bgFor(String pkg) {
        if (cache.containsKey(pkg)) return cache.get(pkg);
        File dir = getExternalFilesDir(null);
        Bitmap b = null;
        if (dir != null) {
            b = decode(new File(new File(dir, "appbg"), pkg + ".jpg"));
            if (b == null) b = decode(new File(dir, "wallpaper.jpg"));
        }
        if (cache.size() > 4) cache.clear();
        cache.put(pkg, b);
        return b;
    }

    private void setBg(Bitmap b) {
        bg.animate().cancel();
        bg.animate().alpha(0f).setDuration(110).withEndAction(() -> {
            bg.setImageBitmap(b);
            bg.animate().alpha(1f).setDuration(260).start();
        }).start();
    }

    private void select(int pos) {
        if (pos < 0 || pos >= apps.size() || pos == selected) return;
        selected = pos;
        ResolveInfo r = apps.get(pos);
        title.setText(r.loadLabel(pm));
        setBg(bgFor(r.activityInfo.packageName));
    }

    private class Tiles extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup p, int t) {
            FrameLayout f = new FrameLayout(Ps5Activity.this);
            f.setLayoutParams(new RecyclerView.LayoutParams(dp(160), dp(88)));
            f.setFocusable(true);
            f.setClickable(true);
            ImageView iv = new ImageView(Ps5Activity.this);
            iv.setBackground(round(0xFF1C1F2A, 10));
            iv.setClipToOutline(true);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -1);
            lp.setMargins(dp(16), dp(10), dp(16), dp(10));
            f.addView(iv, lp);
            return new RecyclerView.ViewHolder(f) { };
        }

        @Override public void onBindViewHolder(RecyclerView.ViewHolder vh, int pos) {
            FrameLayout f = (FrameLayout) vh.itemView;
            ImageView iv = (ImageView) f.getChildAt(0);
            ResolveInfo r = apps.get(pos);
            Drawable dr = r.activityInfo.loadBanner(pm);
            boolean banner = dr != null;
            if (!banner) dr = r.loadIcon(pm);
            iv.setScaleType(banner ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.FIT_CENTER);
            int pad = banner ? 0 : dp(8);
            iv.setPadding(pad, pad, pad, pad);
            iv.setImageDrawable(dr);
            f.setOnFocusChangeListener((v, has) -> {
                v.animate().scaleX(has ? 1.3f : 1f).scaleY(has ? 1.3f : 1f).setDuration(140).start();
                iv.setForeground(has ? ring(10) : null);
                if (has) select(vh.getAdapterPosition());
            });
            f.setOnClickListener(v -> open(vh.getAdapterPosition()));
        }

        @Override public int getItemCount() { return apps.size(); }
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        pm = getPackageManager();
        d = getResources().getDisplayMetrics().density;
        loadApps();

        FrameLayout root = new FrameLayout(this);
        root.setClipChildren(false);
        root.setBackgroundColor(0xFF070A14);
        bg = new ImageView(this);
        bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(bg, new FrameLayout.LayoutParams(-1, -1));
        View scrim = new View(this);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0x55000000, 0x00000000}));
        root.addView(scrim, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setClipChildren(false);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2);
        lp.setMargins(dp(40), dp(28), dp(40), 0);
        root.addView(left, lp);

        row = new RecyclerView(this);
        row.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        row.setAdapter(new Tiles());
        row.setItemAnimator(null);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        row.setPadding(dp(8), dp(16), dp(8), dp(16));
        left.addView(row, new LinearLayout.LayoutParams(-1, dp(120)));

        left.addView(new View(this), new LinearLayout.LayoutParams(-1, dp(70)));
        title = text(46, Color.WHITE, true);
        title.setPadding(dp(18), 0, 0, 0);
        left.addView(title);
        sub = text(16, 0xB3FFFFFF, false);
        sub.setText("Tekan OK untuk membuka");
        sub.setPadding(dp(18), dp(2), 0, 0);
        left.addView(sub);
        openBtn = text(18, Color.WHITE, true);
        openBtn.setText("Buka");
        openBtn.setGravity(Gravity.CENTER);
        openBtn.setFocusable(true);
        openBtn.setClickable(true);
        openBtn.setBackground(round(0x55FFFFFF, 26));
        openBtn.setOnFocusChangeListener((v, f) -> {
            openBtn.setBackground(round(f ? 0xFFFFFFFF : 0x55FFFFFF, 26));
            openBtn.setTextColor(f ? 0xFF111111 : Color.WHITE);
        });
        openBtn.setOnClickListener(v -> open(selected));
        LinearLayout.LayoutParams bp0 = new LinearLayout.LayoutParams(dp(160), dp(52));
        bp0.setMargins(dp(18), dp(22), 0, 0);
        left.addView(openBtn, bp0);

        LinearLayout br = new LinearLayout(this);
        br.setOrientation(LinearLayout.VERTICAL);
        br.setGravity(Gravity.END);
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END);
        bp.setMargins(0, 0, dp(48), dp(36));
        root.addView(br, bp);
        TextClock clk = new TextClock(this);
        clk.setFormat24Hour("HH:mm");
        clk.setFormat12Hour("HH:mm");
        clk.setTextSize(TypedValue.COMPLEX_UNIT_SP, 52);
        clk.setTextColor(Color.WHITE);
        clk.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        clk.setGravity(Gravity.END);
        br.addView(clk);
        TextClock dt = new TextClock(this);
        dt.setFormat24Hour("EEEE, d MMMM");
        dt.setFormat12Hour("EEEE, d MMMM");
        dt.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        dt.setTextColor(0xCCFFFFFF);
        dt.setGravity(Gravity.END);
        br.addView(dt);
        LinearLayout pr = new LinearLayout(this);
        pr.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(-2, -2);
        plp.topMargin = dp(10);
        br.addView(pr, plp);
        for (int i = 0; i < 6; i++) {
            LinearLayout c = new LinearLayout(this);
            c.setOrientation(LinearLayout.VERTICAL);
            c.setGravity(Gravity.CENTER_HORIZONTAL);
            c.setPadding(dp(9), dp(5), dp(9), dp(5));
            TextView n = text(11, 0x99FFFFFF, false);
            n.setText(PrayerTimes.name(i));
            n.setLetterSpacing(0.08f);
            TextView t = text(15, Color.WHITE, false);
            c.addView(n);
            c.addView(t);
            pr.addView(c);
            pCol[i] = c;
            pTime[i] = t;
        }
        pNext = text(14, 0xCCFFFFFF, false);
        pNext.setGravity(Gravity.END);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(-2, -2);
        nlp.topMargin = dp(6);
        br.addView(pNext, nlp);

        setContentView(root);
        h.postDelayed(() -> {
            RecyclerView.ViewHolder vh = row.findViewHolderForAdapterPosition(0);
            if (vh != null) vh.itemView.requestFocus();
        }, 300);
    }

    private void refreshPrayer() {
        try {
            double[] t = PrayerTimes.today();
            double now = PrayerTimes.nowMinutes();
            int nx = PrayerTimes.nextIndex(t, now);
            for (int i = 0; i < 6; i++) pTime[i].setText(PrayerTimes.hm(t[i]));
            if (lit != nx) {
                for (int i = 0; i < 6; i++) pCol[i].setBackground(i == nx ? round(0x33FFFFFF, 10) : null);
                lit = nx;
            }
            int m = PrayerTimes.minutesUntil(nx, t, now);
            String left = m >= 60 ? (m / 60) + " j " + (m % 60) + " mnt" : m + " mnt";
            String nm = PrayerTimes.name(nx);
            pNext.setText("Batam  \u00b7  " + nm.charAt(0) + nm.substring(1).toLowerCase(Locale.ROOT)
                    + " dalam " + left);
        } catch (RuntimeException ignored) {
        }
        h.removeCallbacks(tick);
        h.postDelayed(tick, 30000L);
    }

    @Override protected void onResume() {
        super.onResume();
        refreshPrayer();
    }

    @Override protected void onPause() {
        h.removeCallbacks(tick);
        super.onPause();
    }
}
