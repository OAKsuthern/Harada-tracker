package com.harada.tracker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ReminderReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        int i = intent.getIntExtra("idx", 0);
        Reminders.fire(c, i);
        Store s = new Store(c);
        if (s.remOn(i)) Reminders.schedule(c, i, s.remH(i), s.remM(i));
    }
}
