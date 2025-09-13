package com.example.nwipe_android;

import android.content.Context;
import android.content.SharedPreferences;

public class TargetPrefs {
    private static final String PREF = "securewipe_target";
    private static final String KEY_PATH = "path";
    private static final String KEY_NAME = "name";
    private static final String KEY_FOLDERS = "folders_csv";

    public static void save(Context ctx, String path, String name) {
        SharedPreferences sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        sp.edit().putString(KEY_PATH, path).putString(KEY_NAME, name).apply();
    }

    public static String getPath(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        return sp.getString(KEY_PATH, null);
    }

    public static String getName(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        return sp.getString(KEY_NAME, null);
    }

    public static void clear(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        sp.edit().remove(KEY_PATH).remove(KEY_NAME).remove(KEY_FOLDERS).apply();
    }

    public static void saveFolders(Context ctx, java.util.List<String> folders) {
        SharedPreferences sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        if (folders == null || folders.isEmpty()) {
            sp.edit().remove(KEY_FOLDERS).apply();
            return;
        }
        // Store as CSV; paths likely do not contain '\n'
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < folders.size(); i++) {
            if (folders.get(i) == null) continue;
            if (sb.length() > 0) sb.append('\n');
            sb.append(folders.get(i));
        }
        sp.edit().putString(KEY_FOLDERS, sb.toString()).apply();
    }

    public static java.util.ArrayList<String> getFolders(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        String csv = sp.getString(KEY_FOLDERS, null);
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        if (csv == null || csv.isEmpty()) return out;
        String[] parts = csv.split("\n");
        for (String p : parts) {
            if (p == null || p.trim().isEmpty()) continue;
            out.add(p.trim());
        }
        return out;
    }

    public static void addFolder(Context ctx, String path) {
        if (path == null || path.trim().isEmpty()) return;
        java.util.ArrayList<String> list = getFolders(ctx);
        if (!list.contains(path)) {
            list.add(path);
            saveFolders(ctx, list);
        }
    }

    public static void removeFolder(Context ctx, String path) {
        if (path == null) return;
        java.util.ArrayList<String> list = getFolders(ctx);
        if (list.remove(path)) {
            saveFolders(ctx, list);
        }
    }

    public static boolean isFolderSelected(Context ctx, String path) {
        if (path == null) return false;
        java.util.ArrayList<String> list = getFolders(ctx);
        return list.contains(path);
    }
}
