package ru.deirel.liminalindustries.translation.audit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.locale.Language;
import net.minecraftforge.fml.ModList;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

final class ItemTranslationAudit {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String REQUIRED_LANGUAGE = "ru_ru";
    private static final Path REPORT_PATH = Path.of(
        "liminal-industries-ru-audit",
        "item-name-audit.json"
    );
    private ItemTranslationAudit() {
    }

    static Result run() {
        Minecraft minecraft = Minecraft.getInstance();
        String selectedLanguage = minecraft.getLanguageManager().getSelected();
        if (!REQUIRED_LANGUAGE.equals(selectedLanguage)) {
            return Result.failure("Аудит отменён: выберите язык ru_ru.");
        }

        IJeiRuntime runtime = TranslationAuditJeiPlugin.runtime();
        if (runtime == null) {
            return Result.failure("Аудит отменён: JEI ещё не завершил загрузку.");
        }

        try {
            AuditReport report = audit(minecraft, runtime);
            Path output = minecraft.gameDirectory.toPath().resolve(REPORT_PATH);
            Files.createDirectories(output.getParent());
            Files.writeString(
                output,
                GSON.toJson(report.json()) + "\n",
                StandardCharsets.UTF_8
            );
            LiminalIndustriesTranslationAuditMod.LOGGER.info(
                "Translation audit: {} (report: {})",
                report.success() ? "PASS" : "FAIL",
                output.toAbsolutePath()
            );
            return new Result(
                report.success(),
                "Аудит " + (report.success() ? "пройден" : "не пройден")
                    + ": " + report.checked() + " записей, "
                    + report.failures() + " ошибок. Отчёт: " + output.toAbsolutePath()
            );
        } catch (IOException | RuntimeException exception) {
            LiminalIndustriesTranslationAuditMod.LOGGER.error(
                "Could not complete translation audit",
                exception
            );
            return Result.failure("Ошибка аудита: " + exception.getMessage());
        }
    }

    private static AuditReport audit(Minecraft minecraft, IJeiRuntime runtime) {
        RussianLanguageIndex russian = RussianLanguageIndex.load(
            minecraft.getResourceManager(),
            "ru_ru"
        );
        RussianLanguageIndex english = RussianLanguageIndex.load(
            minecraft.getResourceManager(),
            "en_us"
        );
        Language runtimeRussian = Language.getInstance();
        Language resourceEnglish = ClientLanguage.loadFrom(
            minecraft.getResourceManager(),
            List.of("en_us"),
            false
        );
        AuditContext context = new AuditContext(minecraft, runtime, english);
        Map<String, TranslationAuditIndex.LanguageTarget> languageTargets =
            TranslationAuditIndex.languageTargets();
        Map<String, String> acceptedSameAsEnglish =
            TranslationAuditIndex.acceptedSameAsEnglish();
        Set<TranslationAuditIndex.RegistryTarget> registryTargets =
            TranslationAuditIndex.registryTargets();
        Set<String> targetNamespaces = TranslationAuditIndex.targetNamespaces();
        List<AuditEntry> entries = new ArrayList<>();
        Map<String, Integer> providerCounts = new LinkedHashMap<>();
        Set<String> enabledProviders = AuditSourceConfig.enabledProviders();
        Set<String> availableProviders = AuditProviders.all().stream()
            .map(AuditProvider::id)
            .collect(Collectors.toSet());
        for (String providerId : enabledProviders) {
            if (!availableProviders.contains(providerId)) {
                entries.add(providerError(
                    providerId,
                    new IllegalStateException("configured audit provider is missing")
                ));
            }
        }
        for (AuditProvider provider : AuditProviders.all()) {
            if (!enabledProviders.contains(provider.id())) {
                continue;
            }
            try {
                List<AuditSubject> subjects = provider.discover(context);
                providerCounts.put(provider.id(), subjects.size());
                if (subjects.isEmpty()) {
                    entries.add(providerError(
                        provider.id(),
                        new IllegalStateException("provider discovered no records")
                    ));
                    continue;
                }
                subjects.stream()
                    .map(subject -> inspect(
                        subject,
                        russian,
                        english,
                        runtimeRussian,
                        resourceEnglish,
                        languageTargets,
                        acceptedSameAsEnglish,
                        registryTargets,
                        targetNamespaces
                    ))
                    .sorted(Comparator.comparing(AuditEntry::uid))
                    .forEach(entries::add);
            } catch (RuntimeException exception) {
                providerCounts.put(provider.id(), 0);
                entries.add(providerError(provider.id(), exception));
            }
        }

        int failures = (int) entries.stream().filter(AuditEntry::failure).count();
        boolean success = failures == 0 && russian.errors().isEmpty();
        JsonObject root = new JsonObject();
        root.addProperty("schema", 4);
        root.addProperty("result", success ? "PASS" : "FAIL");
        root.addProperty("generated_at", Instant.now().toString());
        root.addProperty(
            "minecraft_version",
            SharedConstants.getCurrentVersion().getName()
        );
        root.addProperty("selected_language", REQUIRED_LANGUAGE);
        root.addProperty("checked_records", entries.size());
        root.addProperty("failures", failures);
        root.addProperty(
            "target_records",
            entries.stream().filter(AuditEntry::targetScope).count()
        );
        root.addProperty(
            "extra_records",
            entries.stream().filter(entry -> !entry.targetScope()).count()
        );
        root.addProperty("russian_keys", russian.values().size());
        root.addProperty("english_keys", english.values().size());
        root.addProperty("runtime_language_keys", runtimeRussian.getLanguageData().size());

        JsonObject providers = new JsonObject();
        providerCounts.forEach(providers::addProperty);
        root.add("provider_counts", providers);

        Map<AuditStatus, Long> statusCounts = entries.stream().collect(
            Collectors.groupingBy(AuditEntry::status, Collectors.counting())
        );
        JsonObject counts = new JsonObject();
        for (AuditStatus status : AuditStatus.values()) {
            counts.addProperty(status.name(), statusCounts.getOrDefault(status, 0L));
        }
        root.add("status_counts", counts);

        Map<AuditProvenance, Long> provenanceCounts = entries.stream().collect(
            Collectors.groupingBy(AuditEntry::provenance, Collectors.counting())
        );
        JsonObject provenance = new JsonObject();
        for (AuditProvenance value : AuditProvenance.values()) {
            provenance.addProperty(
                value.name(),
                provenanceCounts.getOrDefault(value, 0L)
            );
        }
        root.add("provenance_counts", provenance);

        JsonArray resourceErrors = new JsonArray();
        russian.errors().forEach(resourceErrors::add);
        root.add("resource_errors", resourceErrors);

        JsonArray englishResourceErrors = new JsonArray();
        english.errors().forEach(englishResourceErrors::add);
        root.add("english_resource_errors", englishResourceErrors);

        JsonArray mods = new JsonArray();
        ModList.get().getMods().stream()
            .sorted(Comparator.comparing(mod -> mod.getModId()))
            .forEach(mod -> {
                JsonObject value = new JsonObject();
                value.addProperty("id", mod.getModId());
                value.addProperty("version", mod.getVersion().toString());
                mods.add(value);
            });
        root.add("mods", mods);
        root.add(
            "book_translation_compatibility",
            BookCompatibilityReport.snapshot()
        );

        JsonArray values = new JsonArray();
        entries.forEach(entry -> values.add(entry.json()));
        root.add("entries", values);
        return new AuditReport(success, entries.size(), failures, root);
    }

    private static AuditEntry inspect(
        AuditSubject subject,
        RussianLanguageIndex russian,
        RussianLanguageIndex english,
        Language runtimeRussian,
        Language resourceEnglish,
        Map<String, TranslationAuditIndex.LanguageTarget> languageTargets,
        Map<String, String> acceptedSameAsEnglish,
        Set<TranslationAuditIndex.RegistryTarget> registryTargets,
        Set<String> targetNamespaces
    ) {
        try {
            String displayName = subject.name().getString();
            Map<String, String> runtimeTemplates = ComponentTranslationKeys.collect(
                subject.name(),
                runtimeRussian
            );
            Set<String> keys = new TreeSet<>(runtimeTemplates.keySet());
            boolean targetScope = isTargetScope(
                subject,
                keys,
                languageTargets,
                registryTargets,
                targetNamespaces
            );
            Set<String> verifiedRussianKeys = new TreeSet<>();
            Set<String> sourceChangedKeys = new TreeSet<>();
            Set<String> skippedKeys = new TreeSet<>();
            Map<String, AuditProvenance> keyProvenance = new LinkedHashMap<>();
            for (String key : keys) {
                TranslationAuditIndex.LanguageTarget target =
                    languageTargets.get(key);
                String runtimeTemplate = runtimeTemplates.get(key);
                boolean hasRussianResource = russian.values().containsKey(key);
                boolean runtimeTranslated = !runtimeTemplate.equals(
                    resourceEnglish.getOrDefault(key)
                );
                boolean sameAsEnglish = hasRussianResource
                    && russian.values().get(key).equals(english.values().get(key));
                boolean sourceChanged = target != null
                    && target.source() != null
                    && english.values().get(key) != null
                    && !target.source().equals(english.values().get(key));
                boolean languageNeutral =
                    TranslationTemplate.isLanguageNeutral(runtimeTemplate);
                AuditProvenanceClassifier.Result result =
                    AuditProvenanceClassifier.classify(
                        targetScope,
                        target == null ? null : target.source(),
                        english.values().get(key),
                        hasRussianResource,
                        runtimeTranslated,
                        sameAsEnglish,
                        english.values().get(key) != null
                            && english.values().get(key).equals(
                                acceptedSameAsEnglish.get(key)
                            ),
                        languageNeutral,
                        russian.sources().get(key)
                    );
                keyProvenance.put(key, result.provenance());
                if (sourceChanged) {
                    sourceChangedKeys.add(key);
                }
                if (targetScope && !result.accepted()) {
                    skippedKeys.add(key);
                }
                if (result.accepted()
                    || !targetScope && hasRussianTranslation(
                        key,
                        runtimeTemplate,
                        russian,
                        resourceEnglish
                    )) {
                    verifiedRussianKeys.add(key);
                }
            }
            AuditStatus status = AuditClassifier.classify(
                displayName,
                keys,
                verifiedRussianKeys,
                subject.localizedLiteral()
            );
            Set<String> missing = new TreeSet<>(keys);
            missing.removeAll(verifiedRussianKeys);
            Set<String> sameAsEnglish = new TreeSet<>();
            for (String key : keys) {
                if (russian.values().containsKey(key)
                    && russian.values().get(key).equals(english.values().get(key))) {
                    sameAsEnglish.add(key);
                }
            }
            Map<String, String> translationSources = new LinkedHashMap<>();
            for (String key : keys) {
                String pack = russian.sources().get(key);
                if (pack != null) {
                    translationSources.put(key, pack);
                } else if (verifiedRussianKeys.contains(key)
                    && !runtimeTemplates.get(key).equals(
                        resourceEnglish.getOrDefault(key)
                    )) {
                    translationSources.put(key, "<runtime>");
                }
            }
            AuditProvenance provenance = summarizeProvenance(
                targetScope,
                keyProvenance.values(),
                status
            );
            return new AuditEntry(
                subject.provider(),
                subject.uid(),
                subject.registryType(),
                subject.registryId(),
                subject.descriptionId(),
                displayName,
                subject.discoveredFrom(),
                keys,
                missing,
                sameAsEnglish,
                sourceChangedKeys,
                skippedKeys,
                translationSources,
                keyProvenance,
                targetScope,
                provenance,
                status,
                null
            );
        } catch (RuntimeException exception) {
            return new AuditEntry(
                subject.provider(),
                subject.uid(),
                subject.registryType(),
                subject.registryId(),
                subject.descriptionId(),
                "",
                subject.discoveredFrom(),
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                Map.of(),
                Map.of(),
                true,
                AuditProvenance.MISSING,
                AuditStatus.ERROR,
                exception.toString()
            );
        }
    }

    private static AuditEntry providerError(
        String provider,
        RuntimeException exception
    ) {
        return new AuditEntry(
            provider,
            "provider:" + provider,
            "provider",
            provider,
            "",
            "",
            Set.of("PROVIDER"),
            Set.of(),
            Set.of(),
            Set.of(),
            Set.of(),
            Set.of(),
            Map.of(),
            Map.of(),
            true,
            AuditProvenance.MISSING,
            AuditStatus.ERROR,
            exception.toString()
        );
    }

    private static boolean isTargetScope(
        AuditSubject subject,
        Set<String> keys,
        Map<String, TranslationAuditIndex.LanguageTarget> languageTargets,
        Set<TranslationAuditIndex.RegistryTarget> registryTargets,
        Set<String> targetNamespaces
    ) {
        if ("item".equals(subject.registryType())
            || "block".equals(subject.registryType())) {
            return registryTargets.contains(
                new TranslationAuditIndex.RegistryTarget(
                    subject.registryType(),
                    subject.registryId()
                )
            );
        }
        if (keys.stream().anyMatch(languageTargets::containsKey)) {
            return true;
        }
        int separator = subject.registryId().indexOf(':');
        return separator > 0
            && targetNamespaces.contains(
                subject.registryId().substring(0, separator)
            );
    }

    private static AuditProvenance summarizeProvenance(
        boolean targetScope,
        Iterable<AuditProvenance> keyProvenance,
        AuditStatus status
    ) {
        if (!targetScope) {
            return AuditProvenance.EXTRA_MOD;
        }
        Set<AuditProvenance> values = new TreeSet<>(
            Comparator.comparing(Enum::name)
        );
        keyProvenance.forEach(values::add);
        if (values.isEmpty()) {
            return status.isFailure()
                ? AuditProvenance.MISSING
                : AuditProvenance.NATIVE_TRANSLATION;
        }
        if (values.contains(AuditProvenance.SOURCE_CHANGED)) {
            return AuditProvenance.SOURCE_CHANGED;
        }
        if (values.contains(AuditProvenance.SAME_AS_ENGLISH)) {
            return AuditProvenance.SAME_AS_ENGLISH;
        }
        if (values.contains(AuditProvenance.MISSING)) {
            return AuditProvenance.MISSING;
        }
        return values.size() == 1
            ? values.iterator().next()
            : AuditProvenance.MIXED;
    }

    private static boolean hasRussianTranslation(
        String key,
        String runtimeTemplate,
        RussianLanguageIndex russian,
        Language resourceEnglish
    ) {
        return russian.values().containsKey(key)
            || !runtimeTemplate.equals(resourceEnglish.getOrDefault(key))
            || TranslationTemplate.isLanguageNeutral(runtimeTemplate);
    }

    record Result(boolean success, String message) {
        static Result failure(String message) {
            return new Result(false, message);
        }
    }

    private record AuditReport(boolean success, int checked, int failures, JsonObject json) {
    }

    private record AuditEntry(
        String provider,
        String uid,
        String registryType,
        String registryId,
        String descriptionId,
        String displayName,
        Set<String> discoveredFrom,
        Set<String> translationKeys,
        Set<String> missingRussianKeys,
        Set<String> sameAsEnglishKeys,
        Set<String> sourceChangedKeys,
        Set<String> skippedTranslationKeys,
        Map<String, String> translationSources,
        Map<String, AuditProvenance> translationProvenance,
        boolean targetScope,
        AuditProvenance provenance,
        AuditStatus status,
        String error
    ) {
        boolean failure() {
            return targetScope && status.isFailure();
        }

        JsonObject json() {
            JsonObject value = new JsonObject();
            value.addProperty("provider", provider);
            value.addProperty("uid", uid);
            value.addProperty("registry_type", registryType);
            value.addProperty("registry_id", registryId);
            if ("item".equals(registryType)) {
                value.addProperty("item_id", registryId);
            }
            value.addProperty("description_id", descriptionId);
            value.addProperty("display_name", displayName);
            value.add("discovered_from", strings(discoveredFrom));
            value.add("translation_keys", strings(translationKeys));
            value.add("missing_russian_keys", strings(missingRussianKeys));
            value.add("same_as_english_keys", strings(sameAsEnglishKeys));
            value.add("source_changed_keys", strings(sourceChangedKeys));
            value.add(
                "skipped_translation_keys",
                strings(skippedTranslationKeys)
            );

            JsonObject sources = new JsonObject();
            translationSources.forEach(sources::addProperty);
            value.add("translation_sources", sources);
            JsonObject provenanceByKey = new JsonObject();
            translationProvenance.forEach(
                (key, provenance) ->
                    provenanceByKey.addProperty(key, provenance.name())
            );
            value.add("translation_provenance", provenanceByKey);
            value.addProperty("target_scope", targetScope);
            value.addProperty("provenance", provenance.name());
            value.addProperty("status", status.name());
            if (error != null) {
                value.addProperty("error", error);
            }
            return value;
        }

        private static JsonArray strings(Iterable<String> strings) {
            JsonArray result = new JsonArray();
            strings.forEach(result::add);
            return result;
        }
    }
}
