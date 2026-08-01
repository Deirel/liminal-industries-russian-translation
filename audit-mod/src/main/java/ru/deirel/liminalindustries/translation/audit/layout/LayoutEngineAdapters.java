package ru.deirel.liminalindustries.translation.audit.layout;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public final class LayoutEngineAdapters {
    private static final List<Registration> REGISTRATIONS = List.of(
        new Registration("patchouli", PatchouliLayoutAdapter::new),
        new Registration("mantle", MantleLayoutAdapter::new)
    );
    private static final Map<String, Registration> BY_ENGINE = index();

    private LayoutEngineAdapters() {
    }

    public static List<String> engines() {
        return REGISTRATIONS.stream()
            .map(Registration::engine)
            .toList();
    }

    static List<LayoutEngineAdapter> createAll() {
        return REGISTRATIONS.stream()
            .map(Registration::create)
            .toList();
    }

    static LayoutEngineAdapter create(String engine) {
        Registration registration = BY_ENGINE.get(engine);
        if (registration == null) {
            throw new IllegalArgumentException("Unknown layout adapter: " + engine);
        }
        return registration.create();
    }

    private static Map<String, Registration> index() {
        Map<String, Registration> result = new LinkedHashMap<>();
        for (Registration registration : REGISTRATIONS) {
            if (!registration.engine().matches("[a-z0-9_]+")) {
                throw new IllegalStateException(
                    "Invalid layout adapter name: " + registration.engine()
                );
            }
            if (result.put(registration.engine(), registration) != null) {
                throw new IllegalStateException(
                    "Duplicate layout adapter: " + registration.engine()
                );
            }
        }
        return Map.copyOf(result);
    }

    private record Registration(
        String engine,
        Supplier<LayoutEngineAdapter> factory
    ) {
        private LayoutEngineAdapter create() {
            LayoutEngineAdapter adapter = factory.get();
            if (!engine.equals(adapter.engine())) {
                throw new IllegalStateException(
                    "Layout adapter registered as " + engine
                        + " but reports " + adapter.engine()
                );
            }
            return adapter;
        }
    }
}
