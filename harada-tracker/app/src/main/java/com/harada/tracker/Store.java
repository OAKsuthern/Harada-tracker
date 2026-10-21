package com.harada.tracker;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** All app data lives in one SharedPreferences file. */
public class Store {
    static final double THRESHOLD = 0.8;

    static final String[] DEFAULT_ITEMS = {
            "4am training session",
            "Stretch 15 minutes",
            "MMA / BJJ / wrestling class",
            "Shadowbox 3 rounds",
            "Protein with every meal",
            "Drink 3 L of water",
            "Visualize the fight (5 min)",
            "Write in training journal",
            "Study one fight",
            "8 hours in bed"
    };

    static final String[] REMINDER_NAMES = {"Morning nudge", "Evening warning", "Last call"};
    static final int[][] REMINDER_DEFAULTS = {{3, 45}, {20, 0}, {22, 0}};

    static class Item {
        final String id;
        final String name;
        Item(String id, String name) { this.id = id; this.name = name; }
    }

    private final SharedPreferences p;

    Store(Context c) {
        p = c.getApplicationContext().getSharedPreferences("harada", Context.MODE_PRIVATE);
        if (!p.contains("items")) seed();
    }

    private void seed() {
        List<Item> list = new ArrayList<>();
        for (int i = 0; i < DEFAULT_ITEMS.length; i++) list.add(new Item("i" + i, DEFAULT_ITEMS[i]));
        saveItems(list);
        p.edit().putInt("nextId", DEFAULT_ITEMS.length).apply();
    }

    // ---------- dates ----------
    static String key(Calendar c) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(c.getTime());
    }

    static String today() { return key(Calendar.getInstance()); }

    // ---------- items ----------
    List<Item> items() {
        List<Item> list = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(p.getString("items", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                list.add(new Item(o.getString("id"), o.getString("name")));
            }
        } catch (JSONException ignored) { }
        return list;
    }

    private void saveItems(List<Item> list) {
        JSONArray a = new JSONArray();
        try {
            for (Item it : list) {
                JSONObject o = new JSONObject();
                o.put("id", it.id);
                o.put("name", it.name);
                a.put(o);
            }
        } catch (JSONException ignored) { }
        p.edit().putString("items", a.toString()).apply();
    }

    void addItem(String name) {
        int n = p.getInt("nextId", 100);
        List<Item> list = items();
        list.add(new Item("i" + n, name));
        saveItems(list);
        p.edit().putInt("nextId", n + 1).apply();
        syncToday();
    }

    void renameItem(String id, String name) {
        List<Item> list = items();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id.equals(id)) list.set(i, new Item(id, name));
        }
        saveItems(list);
    }

    void deleteItem(String id) {
        List<Item> list = items();
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).id.equals(id)) list.remove(i);
        }
        saveItems(list);
        syncToday();
    }

    void moveItem(String id, int delta) {
        List<Item> list = items();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id.equals(id)) {
                int j = i + delta;
                if (j < 0 || j >= list.size()) return;
                Item t = list.get(j);
                list.set(j, list.get(i));
                list.set(i, t);
                saveItems(list);
                return;
            }
        }
    }

    // ---------- daily records ----------
    private JSONObject day(String day) {
        try { return new JSONObject(p.getString("d_" + day, "{}")); }
        catch (JSONException e) { return new JSONObject(); }
    }

    Set<String> checked(String day) {
        Set<String> s = new HashSet<>();
        JSONArray a = day(day).optJSONArray("c");
        if (a != null) for (int i = 0; i < a.length(); i++) s.add(a.optString(i));
        return s;
    }

    private Set<String> currentIds() {
        Set<String> ids = new HashSet<>();
        for (Item it : items()) ids.add(it.id);
        return ids;
    }

    private void writeDay(String day, Set<String> checked, int total) {
        JSONObject o = new JSONObject();
        try {
            o.put("c", new JSONArray(checked));
            o.put("t", total);
        } catch (JSONException ignored) { }
        p.edit().putString("d_" + day, o.toString()).apply();
        updateBest();
    }

    void setChecked(String id, boolean on) {
        String t = today();
        Set<String> s = checked(t);
        if (on) s.add(id); else s.remove(id);
        s.retainAll(currentIds());
        writeDay(t, s, items().size());
    }

    private void syncToday() {
        String t = today();
        Set<String> s = checked(t);
        s.retainAll(currentIds());
        writeDay(t, s, items().size());
    }

    int doneCount(String day) {
        Set<String> s = checked(day);
        if (day.equals(today())) s.retainAll(currentIds());
        return s.size();
    }

    int total(String day) {
        if (day.equals(today())) return items().size();
        return day(day).optInt("t", 0);
    }

    static int required(int total) {
        return (int) Math.ceil(total * THRESHOLD - 1e-9);
    }

    boolean isRest(String day) { return p.getBoolean("r_" + day, false); }

    boolean kept(String day) {
        if (isRest(day)) return true;
        int t = total(day);
        if (t <= 0) return false;
        return doneCount(day) >= required(t);
    }

    /** Consecutive kept days, ending today if today is kept, otherwise ending yesterday. */
    int streak() {
        Calendar c = Calendar.getInstance();
        if (!kept(key(c))) c.add(Calendar.DAY_OF_YEAR, -1);
        int s = 0;
        while (s < 10000 && kept(key(c))) {
            s++;
            c.add(Calendar.DAY_OF_YEAR, -1);
        }
        return s;
    }

    int best() { return Math.max(p.getInt("best", 0), streak()); }

    private void updateBest() {
        int s = streak();
        if (s > p.getInt("best", 0)) p.edit().putInt("best", s).apply();
    }

    // ---------- rest days: one per Monday-Sunday week ----------
    boolean restUsedElsewhereThisWeek() {
        Calendar c = Calendar.getInstance();
        int diff = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7; // Monday = 0
        c.add(Calendar.DAY_OF_YEAR, -diff);
        String t = today();
        for (int i = 0; i < 7; i++) {
            String k = key(c);
            if (!k.equals(t) && isRest(k)) return true;
            c.add(Calendar.DAY_OF_YEAR, 1);
        }
        return false;
    }

    void setRestToday(boolean on) {
        p.edit().putBoolean("r_" + today(), on).apply();
        updateBest();
    }

    // ---------- reminders ----------
    boolean remOn(int i) { return p.getBoolean("rem_on_" + i, true); }
    int remH(int i) { return p.getInt("rem_h_" + i, REMINDER_DEFAULTS[i][0]); }
    int remM(int i) { return p.getInt("rem_m_" + i, REMINDER_DEFAULTS[i][1]); }

    void setRem(int i, boolean on, int h, int m) {
        p.edit().putBoolean("rem_on_" + i, on).putInt("rem_h_" + i, h).putInt("rem_m_" + i, m).apply();
    }
}
