package com.harada.tracker;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import java.util.Calendar;

public class Reminders {
    static final String CHANNEL = "streak";

    static void createChannel(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Streak reminders", NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("Reminders so you don't lose your streak");
        nm.createNotificationChannel(ch);
    }

    private static PendingIntent alarmIntent(Context c, int i) {
        Intent it = new Intent(c, ReminderReceiver.class);
        it.setAction("com.harada.tracker.REMIND_" + i);
        it.putExtra("idx", i);
        return PendingIntent.getBroadcast(c, i, it, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void scheduleAll(Context c) {
        Store s = new Store(c);
        for (int i = 0; i < 3; i++) {
            if (s.remOn(i)) schedule(c, i, s.remH(i), s.remM(i));
            else cancel(c, i);
        }
    }

    static void schedule(Context c, int i, int h, int m) {
        Calendar t = Calendar.getInstance();
        t.set(Calendar.HOUR_OF_DAY, h);
        t.set(Calendar.MINUTE, m);
        t.set(Calendar.SECOND, 0);
        t.set(Calendar.MILLISECOND, 0);
        if (!t.after(Calendar.getInstance())) t.add(Calendar.DAY_OF_YEAR, 1);

        AlarmManager am = c.getSystemService(AlarmManager.class);
        PendingIntent pi = alarmIntent(c, i);
        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t.getTimeInMillis(), pi);
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t.getTimeInMillis(), pi);
            }
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t.getTimeInMillis(), pi);
        }
    }

    static void cancel(Context c, int i) {
        c.getSystemService(AlarmManager.class).cancel(alarmIntent(c, i));
    }

    static boolean canNotify(Context c) {
        if (Build.VERSION.SDK_INT >= 33
                && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        return c.getSystemService(NotificationManager.class).areNotificationsEnabled();
    }

    static void show(Context c, int id, String title, String text) {
        if (!canNotify(c)) return;
        createChannel(c);
        Intent open = new Intent(c, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat)
                .setColor(0xFFD7262E)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build();
        c.getSystemService(NotificationManager.class).notify(100 + id, n);
    }

    /** Builds and shows the reminder for slot i. Returns false if nothing needed showing. */
    static boolean fire(Context c, int i) {
        Store s = new Store(c);
        String t = Store.today();
        int total = s.total(t);
        int done = s.doneCount(t);
        int need = Store.required(total);
        int streak = s.streak();
        boolean kept = s.kept(t);

        if (i == 0) {
            String text = streak > 0
                    ? "You're on a " + streak + "-day streak. Check off " + need + " of " + total + " today to keep it going."
                    : "Start a streak today. Check off " + need + " of " + total + ".";
            show(c, i, "Time to work", text);
            return true;
        }
        if (kept) return false;
        int left = Math.max(0, need - done);
        String goal = streak > 0 ? "to keep your " + streak + "-day streak." : "to start your streak.";
        String text = "You've done " + done + " of " + total + ". " + left + " more " + goal;
        show(c, i, i == 1 ? "Streak at risk" : "Last call before midnight", text);
        return true;
    }
}
