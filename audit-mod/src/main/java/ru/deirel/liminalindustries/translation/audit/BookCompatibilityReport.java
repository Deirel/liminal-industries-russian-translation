package ru.deirel.liminalindustries.translation.audit;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

final class BookCompatibilityReport {
    private static final String DIAGNOSTICS_CLASS =
        "ru.deirel.liminalindustries.translation.BookTranslationDiagnostics";

    private BookCompatibilityReport() {
    }

    static JsonArray snapshot() {
        try {
            Class<?> type = Class.forName(DIAGNOSTICS_CLASS);
            Method report = type.getMethod("reportJson");
            String json = (String) report.invoke(null);
            return JsonParser.parseString(json).getAsJsonArray();
        } catch (ClassNotFoundException exception) {
            return new JsonArray();
        } catch (
            NoSuchMethodException
            | IllegalAccessException
            | InvocationTargetException
            | RuntimeException exception
        ) {
            LiminalIndustriesTranslationAuditMod.LOGGER.warn(
                "Could not collect book translation compatibility diagnostics",
                exception
            );
            return new JsonArray();
        }
    }
}
