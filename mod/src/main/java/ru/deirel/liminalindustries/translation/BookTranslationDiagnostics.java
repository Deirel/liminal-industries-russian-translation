package ru.deirel.liminalindustries.translation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class BookTranslationDiagnostics {
    private static final Gson GSON = new GsonBuilder().create();
    private static final Map<String, Entry> ENTRIES = new ConcurrentHashMap<>();

    public record Entry(
        String resource,
        String status,
        int translatedFields,
        List<String> skippedFields,
        String detail
    ) {
        public Entry {
            skippedFields = List.copyOf(skippedFields);
        }
    }

    private BookTranslationDiagnostics() {
    }

    static void record(Entry entry) {
        ENTRIES.put(entry.resource(), entry);
    }

    public static String reportJson() {
        List<Entry> entries = ENTRIES.values().stream()
            .sorted(Comparator.comparing(Entry::resource))
            .toList();
        return GSON.toJson(entries);
    }

    static void clear() {
        ENTRIES.clear();
    }
}
