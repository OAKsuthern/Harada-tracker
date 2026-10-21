package com.harada.tracker;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {
    static final int BG = 0xFF0E1116;
    static final int CARD = 0xFF181D25;
    static final int LINE = 0xFF2A313C;
    static final int INK = 0xFFECEFF3;
    static final int MUTED = 0xFF8D97A5;
    static final int RED = 0xFFE2373F;
    static final int GREEN = 0xFF3DBE7E;

    private Store store;
    private ScrollView scroll;
    private LinearLayout root;
    private Typeface display;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        store = new Store(this);
        display = Typeface.create("sans-serif-condensed", Typeface.BOLD);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        scroll.setFitsSystemWindows(true);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(40));
        scroll.addView(root, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);

        Reminders.createChannel(this);
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
        Reminders.scheduleAll(this);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        render();
    }

    // ---------------- UI ----------------
    private void render() {
        int y = scroll.getScrollY();
        root.removeAllViews();
        String today = Store.today();
        int total = store.total(today);
        int done = store.doneCount(today);
        int need = Store.required(total);
        boolean rest = store.isRest(today);
        boolean kept = store.kept(today);
        int streak = store.streak();

        // Header
        TextView eyebrow = text("HARADA TRACKER", 12, MUTED);
        eyebrow.setLetterSpacing(0.15f);
        root.addView(eyebrow);

        TextView big = text(String.valueOf(streak), 72, kept ? GREEN : INK);
        big.setTypeface(display);
        big.setIncludeFontPadding(false);
        root.addView(big, margins(0, 8, 0, 0));
        root.addView(text((streak == 1 ? "day streak" : "day streak") + "  ·  best " + store.best(), 15, MUTED));

        // Today card
        LinearLayout card = card();
        TextView status;
        if (rest) status = text("Rest day. Streak protected.", 16, GREEN);
        else if (kept) status = text("Streak safe for today", 16, GREEN);
        else status = text((need - done) + " more to keep the streak", 16, INK);
        status.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(status);
        card.addView(text(done + " of " + total + " done  ·  need " + need + " (80%)", 13, MUTED), margins(0, 4, 0, 10));
        ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(Math.max(total, 1));
        bar.setProgress(done);
        bar.setProgressTintList(ColorStateList.valueOf(kept ? GREEN : RED));
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(LINE));
        card.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(10)));
        root.addView(card, margins(0, 20, 0, 0));

        // Checklist
        root.addView(sectionTitle("Today"));
        LinearLayout list = card();
        list.setPadding(dp(8), dp(4), dp(8), dp(4));
        Set<String> checked = store.checked(today);
        List<Store.Item> items = store.items();
        for (Store.Item it : items) {
            CheckBox cb = new CheckBox(this);
            cb.setText(it.name);
            cb.setTextSize(16);
            cb.setTextColor(checked.contains(it.id) ? MUTED : INK);
            cb.setButtonTintList(ColorStateList.valueOf(GREEN));
            cb.setPadding(dp(8), dp(14), dp(8), dp(14));
            cb.setChecked(checked.contains(it.id));
            cb.setOnCheckedChangeListener((v, on) -> {
                boolean wasKept = store.kept(Store.today());
                store.setChecked(it.id, on);
                if (!wasKept && store.kept(Store.today())) {
                    Toast.makeText(this, "Streak safe for today: " + store.streak() + " days", Toast.LENGTH_SHORT).show();
                }
                root.post(this::render);
            });
            cb.setOnLongClickListener(v -> { editItem(it); return true; });
            list.addView(cb, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        if (items.isEmpty()) list.addView(text("No items yet. Add one below.", 14, MUTED), margins(8, 12, 8, 12));
        root.addView(list);
        root.addView(text("Long-press an item to rename, move or delete it.", 12, MUTED), margins(4, 6, 0, 0));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button add = button("+ Add item", false);
        add.setOnClickListener(v -> addItem());
        actions.addView(add, weight());
        Button restBtn;
        if (rest) {
            restBtn = button("Undo rest day", false);
            restBtn.setOnClickListener(v -> { store.setRestToday(false); render(); });
        } else if (store.restUsedElsewhereThisWeek()) {
            restBtn = button("Rest day used", false);
            restBtn.setEnabled(false);
            restBtn.setAlpha(0.45f);
        } else {
            restBtn = button("Take rest day", false);
            restBtn.setOnClickListener(v -> confirmRest());
        }
        LinearLayout.LayoutParams rp = weight();
        rp.leftMargin = dp(10);
        actions.addView(restBtn, rp);
        root.addView(actions, margins(0, 12, 0, 0));

        // Last 7 days
        root.addView(sectionTitle("Last 7 days"));
        LinearLayout week = card();
        week.setOrientation(LinearLayout.HORIZONTAL);
        SimpleDateFormat letter = new SimpleDateFormat("EEE", Locale.US);
        for (int i = 6; i >= 0; i--) {
            Calendar c = Calendar.getInstance();
            c.add(Calendar.DAY_OF_YEAR, -i);
            String k = Store.key(c);
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER_HORIZONTAL);
            boolean k2 = store.kept(k);
            TextView dot = text(k2 ? "●" : (i == 0 ? "○" : "●"), 22, k2 ? GREEN : (i == 0 ? MUTED : LINE));
            dot.setGravity(Gravity.CENTER);
            col.addView(dot);
            TextView lab = text(i == 0 ? "Today" : letter.format(c.getTime()), 11, MUTED);
            lab.setGravity(Gravity.CENTER);
            col.addView(lab);
            week.addView(col, weight());
        }
        root.addView(week);

        // Reminders
        root.addView(sectionTitle("Reminders"));
        LinearLayout rem = card();
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            Switch sw = new Switch(this);
            sw.setText(Store.REMINDER_NAMES[i]);
            sw.setTextColor(INK);
            sw.setTextSize(15);
            sw.setChecked(store.remOn(i));
            sw.setThumbTintList(ColorStateList.valueOf(store.remOn(i) ? GREEN : MUTED));
            sw.setOnCheckedChangeListener((v, on) -> {
                store.setRem(idx, on, store.remH(idx), store.remM(idx));
                Reminders.scheduleAll(this);
                root.post(this::render);
            });
            row.addView(sw, weight());
            Button time = button(fmt(store.remH(i), store.remM(i)), false);
            time.setOnClickListener(v -> new TimePickerDialog(this, android.R.style.Theme_DeviceDefault_Dialog_Alert,
                    (tp, h, m) -> {
                        store.setRem(idx, true, h, m);
                        Reminders.scheduleAll(this);
                        render();
                        Toast.makeText(this, Store.REMINDER_NAMES[idx] + " set for " + fmt(h, m), Toast.LENGTH_SHORT).show();
                    }, store.remH(idx), store.remM(idx), false).show());
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tl.leftMargin = dp(12);
            row.addView(time, tl);
            rem.addView(row, margins(0, i == 0 ? 0 : 8, 0, 0));
        }
        rem.addView(text("The morning nudge always comes. The evening warning and last call only come if today isn't safe yet.", 12, MUTED), margins(0, 12, 0, 0));
        root.addView(rem);

        // Reliability
        if (!Reminders.canNotify(this)) {
            LinearLayout warn = card();
            warn.addView(text("Notifications are off, so reminders can't reach you.", 14, RED));
            Button on = button("Turn on notifications", true);
            on.setOnClickListener(v -> {
                Intent it = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                it.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
                startSafe(it);
            });
            warn.addView(on, margins(0, 10, 0, 0));
            root.addView(warn, margins(0, 12, 0, 0));
        }
        PowerManager pm = getSystemService(PowerManager.class);
        if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
            LinearLayout bat = card();
            bat.addView(text("Samsung phones can silence reminders to save battery. Allow this app to run unrestricted so they always arrive.", 13, MUTED));
            Button allow = button("Allow reminders in background", true);
            allow.setOnClickListener(v -> {
                Intent it = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                it.setData(Uri.parse("package:" + getPackageName()));
                if (!startSafe(it)) startSafe(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            });
            bat.addView(allow, margins(0, 10, 0, 0));
            root.addView(bat, margins(0, 12, 0, 0));
        }
        Button test = button("Send a test notification", false);
        test.setOnClickListener(v -> {
            if (!Reminders.canNotify(this)) {
                Toast.makeText(this, "Turn on notifications first", Toast.LENGTH_SHORT).show();
                return;
            }
            Reminders.show(this, 9, "Test reminder", "Reminders are working. Current streak: " + store.streak() + " days.");
        });
        root.addView(test, margins(0, 12, 0, 0));

        scroll.post(() -> scroll.scrollTo(0, y));
    }

    // ---------------- dialogs ----------------
    private void addItem() {
        EditText in = input("");
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("New daily item")
                .setView(wrap(in))
                .setPositiveButton("Add", (d, w) -> {
                    String s = in.getText().toString().trim();
                    if (!s.isEmpty()) { store.addItem(s); render(); }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void editItem(Store.Item it) {
        String[] opts = {"Rename", "Move up", "Move down", "Delete"};
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(it.name)
                .setItems(opts, (d, w) -> {
                    if (w == 0) renameItem(it);
                    else if (w == 1) { store.moveItem(it.id, -1); render(); }
                    else if (w == 2) { store.moveItem(it.id, 1); render(); }
                    else confirmDelete(it);
                })
                .show();
    }

    private void renameItem(Store.Item it) {
        EditText in = input(it.name);
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Rename item")
                .setView(wrap(in))
                .setPositiveButton("Save", (d, w) -> {
                    String s = in.getText().toString().trim();
                    if (!s.isEmpty()) { store.renameItem(it.id, s); render(); }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmDelete(Store.Item it) {
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Delete \"" + it.name + "\"?")
                .setMessage("Past days keep their results. Today's target updates.")
                .setPositiveButton("Delete", (d, w) -> { store.deleteItem(it.id); render(); })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmRest() {
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Take today as a rest day?")
                .setMessage("Your streak is protected today. You get one rest day per week (Monday to Sunday).")
                .setPositiveButton("Rest today", (d, w) -> { store.setRestToday(true); render(); })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ---------------- helpers ----------------
    private boolean startSafe(Intent it) {
        try { startActivity(it); return true; } catch (Exception e) { return false; }
    }

    private EditText input(String value) {
        EditText in = new EditText(this);
        in.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        in.setSingleLine(true);
        in.setText(value);
        in.setSelection(value.length());
        return in;
    }

    private View wrap(View v) {
        FrameLayout f = new FrameLayout(this);
        f.setPadding(dp(20), dp(8), dp(20), 0);
        f.addView(v);
        return f;
    }

    private static String fmt(int h, int m) {
        int hh = h % 12 == 0 ? 12 : h % 12;
        return String.format(Locale.US, "%d:%02d %s", hh, m, h < 12 ? "AM" : "PM");
    }

    private TextView text(String s, float sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        return t;
    }

    private TextView sectionTitle(String s) {
        TextView t = text(s.toUpperCase(Locale.US), 13, MUTED);
        t.setLetterSpacing(0.12f);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(dp(4), dp(24), 0, dp(8));
        return t;
    }

    private LinearLayout card() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(16), dp(16), dp(16), dp(16));
        GradientDrawable g = new GradientDrawable();
        g.setColor(CARD);
        g.setCornerRadius(dp(14));
        l.setBackground(g);
        return l;
    }

    private Button button(String label, boolean primary) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setTextColor(primary ? 0xFFFFFFFF : INK);
        GradientDrawable g = new GradientDrawable();
        g.setColor(primary ? RED : CARD);
        g.setStroke(dp(1), primary ? RED : LINE);
        g.setCornerRadius(dp(12));
        b.setBackground(g);
        b.setPadding(dp(14), dp(10), dp(14), dp(10));
        return b;
    }

    private LinearLayout.LayoutParams margins(int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(dp(l), dp(t), dp(r), dp(b));
        return p;
    }

    private LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
