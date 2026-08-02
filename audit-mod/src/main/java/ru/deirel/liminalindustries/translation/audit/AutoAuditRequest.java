package ru.deirel.liminalindustries.translation.audit;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import java.util.Set;

record AutoAuditRequest(int schema, String audit, boolean exitWhenDone) {
    private static final Gson GSON = new Gson();
    private static final Set<String> AUDITS = Set.of(
        "texts",
        "books",
        "patchouli",
        "mantle",
        "ie"
    );

    static AutoAuditRequest parse(String json) {
        AutoAuditRequest request;
        try {
            request = GSON.fromJson(json, AutoAuditRequest.class);
        } catch (JsonParseException exception) {
            throw new IllegalArgumentException("Invalid auto-audit request JSON", exception);
        }
        if (request == null || request.schema != 1) {
            throw new IllegalArgumentException("Unsupported auto-audit request schema");
        }
        if (!AUDITS.contains(request.audit)) {
            throw new IllegalArgumentException(
                "Unknown auto-audit mode: " + request.audit
            );
        }
        return request;
    }
}
