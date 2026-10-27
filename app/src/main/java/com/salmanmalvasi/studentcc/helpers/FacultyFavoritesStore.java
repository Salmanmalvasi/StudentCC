package com.salmanmalvasi.studentcc.helpers;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.salmanmalvasi.studentcc.models.Faculty;

public final class FacultyFavoritesStore {

    private static final String PREF_KEY = "faculty_favorites";

    private FacultyFavoritesStore() {
        // Utility class
    }

    @NonNull
    public static Set<String> getFavorites(@NonNull Context context) {
        SharedPreferences sharedPreferences = SettingsRepository.getSharedPreferences(context.getApplicationContext());
        Set<String> storedSet = sharedPreferences.getStringSet(PREF_KEY, Collections.emptySet());
        if (storedSet == null) {
            return new HashSet<>();
        }
        return new HashSet<>(storedSet);
    }

    public static boolean isFavorite(@NonNull Context context, @NonNull Faculty faculty) {
        return getFavorites(context).contains(buildKey(faculty));
    }

    public static boolean toggleFavorite(@NonNull Context context, @NonNull Faculty faculty) {
        Set<String> favorites = getFavorites(context);
        String key = buildKey(faculty);
        boolean added;
        if (favorites.contains(key)) {
            favorites.remove(key);
            added = false;
        } else {
            favorites.add(key);
            added = true;
        }
        saveFavorites(context, favorites);
        return added;
    }

    public static Comparator<Faculty> favoriteFirstComparator(@NonNull Context context) {
        final Set<String> favorites = getFavorites(context);
        return (a, b) -> {
            boolean aFavorite = favorites.contains(buildKey(a));
            boolean bFavorite = favorites.contains(buildKey(b));

            if (aFavorite && !bFavorite) return -1;
            if (!aFavorite && bFavorite) return 1;

            // Fall back to name comparison for stability
            return a.getEmpName().compareToIgnoreCase(b.getEmpName());
        };
    }

    public static List<Faculty> filterFavorites(@NonNull Context context, @NonNull List<Faculty> source) {
        Set<String> favorites = getFavorites(context);
        List<Faculty> result = new ArrayList<>();
        for (Faculty faculty : source) {
            if (favorites.contains(buildKey(faculty))) {
                result.add(faculty);
            }
        }
        return result;
    }

    public static boolean isFavorite(@NonNull Set<String> favorites, @NonNull Faculty faculty) {
        return favorites.contains(buildKey(faculty));
    }

    private static void saveFavorites(@NonNull Context context, @NonNull Set<String> favorites) {
        SettingsRepository.getSharedPreferences(context.getApplicationContext())
                .edit()
                .putStringSet(PREF_KEY, favorites)
                .apply();
    }

    @NonNull
    private static String buildKey(@NonNull Faculty faculty) {
        String id = faculty.getEmpId();
        if (!TextUtils.isEmpty(id)) {
            return "ID:" + id.trim().toUpperCase(Locale.US);
        }
        return "NAME:" + faculty.getEmpName().trim().toUpperCase(Locale.US);
    }
}
