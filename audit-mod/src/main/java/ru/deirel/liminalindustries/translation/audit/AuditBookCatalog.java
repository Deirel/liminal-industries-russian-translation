package ru.deirel.liminalindustries.translation.audit;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class AuditBookCatalog {
    private AuditBookCatalog() {
    }

    static List<AuditBook> collect() {
        Set<String> enabledProviders = AuditSourceConfig.enabledProviders();
        Map<String, AuditBook> books = new LinkedHashMap<>();
        for (AuditProvider provider : AuditProviders.all()) {
            if (!enabledProviders.contains(provider.id())) {
                continue;
            }
            for (AuditBook book : provider.bookStacks()) {
                AuditBook previous = books.putIfAbsent(book.logicalId(), book);
                if (previous != null) {
                    throw new IllegalStateException(
                        "duplicate audit book " + book.logicalId()
                    );
                }
            }
        }
        return List.copyOf(books.values());
    }
}
