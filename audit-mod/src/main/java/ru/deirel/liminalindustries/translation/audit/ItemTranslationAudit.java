package ru.deirel.liminalindustries.translation.audit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
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
                "Item translation audit: {} (report: {})",
                report.success() ? "PASS" : "FAIL",
                output.toAbsolutePath()
            );
            return new Result(
                report.success(),
                "Аудит " + (report.success() ? "пройден" : "не пройден")
                    + ": " + report.checked() + " вариантов, "
                    + report.failures() + " ошибок. Отчёт: " + output.toAbsolutePath()
            );
        } catch (IOException | RuntimeException exception) {
            LiminalIndustriesTranslationAuditMod.LOGGER.error(
                "Could not complete item translation audit",
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
        IIngredientManager ingredients = runtime.getIngredientManager();
        IIngredientHelper<ItemStack> helper = ingredients.getIngredientHelper(
            VanillaTypes.ITEM_STACK
        );
        Collection<ItemStack> jeiStacks = ingredients.getAllIngredients(
            VanillaTypes.ITEM_STACK
        );

        Map<String, Candidate> candidates = new LinkedHashMap<>();
        for (ItemStack stack : jeiStacks) {
            if (!stack.isEmpty()) {
                addCandidate(candidates, helper, stack, "JEI");
            }
        }
        for (Item item : ForgeRegistries.ITEMS) {
            if (item != Items.AIR) {
                ItemStack stack = new ItemStack(item);
                if (!stack.isEmpty()) {
                    addCandidate(candidates, helper, stack, "REGISTRY");
                }
            }
        }

        List<AuditEntry> entries = candidates.values().stream()
            .map(candidate -> inspect(
                candidate,
                russian,
                english,
                runtimeRussian,
                resourceEnglish
            ))
            .sorted(Comparator.comparing(AuditEntry::uid))
            .toList();
        int failures = (int) entries.stream().filter(AuditEntry::failure).count();
        boolean success = failures == 0 && russian.errors().isEmpty();

        JsonObject root = new JsonObject();
        root.addProperty("schema", 2);
        root.addProperty("result", success ? "PASS" : "FAIL");
        root.addProperty("generated_at", Instant.now().toString());
        root.addProperty(
            "minecraft_version",
            SharedConstants.getCurrentVersion().getName()
        );
        root.addProperty("selected_language", REQUIRED_LANGUAGE);
        root.addProperty("jei_item_stacks", jeiStacks.size());
        root.addProperty("registered_items", ForgeRegistries.ITEMS.getValues().size());
        root.addProperty("checked_variants", entries.size());
        root.addProperty("failures", failures);
        root.addProperty("russian_keys", russian.values().size());
        root.addProperty("english_keys", english.values().size());
        root.addProperty("runtime_language_keys", runtimeRussian.getLanguageData().size());

        Map<AuditStatus, Long> statusCounts = entries.stream().collect(
            Collectors.groupingBy(AuditEntry::status, Collectors.counting())
        );
        JsonObject counts = new JsonObject();
        for (AuditStatus status : AuditStatus.values()) {
            counts.addProperty(status.name(), statusCounts.getOrDefault(status, 0L));
        }
        root.add("status_counts", counts);

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

        JsonArray values = new JsonArray();
        entries.forEach(entry -> values.add(entry.json()));
        root.add("entries", values);
        return new AuditReport(success, entries.size(), failures, root);
    }

    private static void addCandidate(
        Map<String, Candidate> candidates,
        IIngredientHelper<ItemStack> helper,
        ItemStack stack,
        String source
    ) {
        String uid;
        try {
            uid = helper.getUniqueId(stack, UidContext.Ingredient);
        } catch (RuntimeException exception) {
            ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
            uid = "unresolved:" + itemId + ":" + Integer.toHexString(stack.hashCode());
        }
        String auditUid = uid;
        Candidate candidate = candidates.computeIfAbsent(
            auditUid,
            ignored -> new Candidate(auditUid, stack.copy(), new TreeSet<>())
        );
        candidate.sources().add(source);
    }

    private static AuditEntry inspect(
        Candidate candidate,
        RussianLanguageIndex russian,
        RussianLanguageIndex english,
        Language runtimeRussian,
        Language resourceEnglish
    ) {
        ItemStack stack = candidate.stack();
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
        try {
            Component hoverName = stack.getHoverName();
            String displayName = hoverName.getString();
            Map<String, String> runtimeTemplates = ComponentTranslationKeys.collect(
                hoverName,
                runtimeRussian
            );
            Set<String> keys = new TreeSet<>(runtimeTemplates.keySet());
            Set<String> verifiedRussianKeys = keys.stream()
                .filter(key -> hasRussianTranslation(
                    key,
                    runtimeTemplates.get(key),
                    russian,
                    resourceEnglish
                ))
                .collect(Collectors.toCollection(TreeSet::new));
            AuditStatus status = AuditClassifier.classify(
                displayName,
                keys,
                verifiedRussianKeys
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
            return new AuditEntry(
                candidate.uid(),
                itemId.toString(),
                stack.getDescriptionId(),
                displayName,
                candidate.sources(),
                keys,
                missing,
                sameAsEnglish,
                translationSources,
                status,
                null
            );
        } catch (RuntimeException exception) {
            return new AuditEntry(
                candidate.uid(),
                itemId.toString(),
                stack.getDescriptionId(),
                "",
                candidate.sources(),
                Set.of(),
                Set.of(),
                Set.of(),
                Map.of(),
                AuditStatus.ERROR,
                exception.toString()
            );
        }
    }

    private static boolean hasRussianTranslation(
        String key,
        String runtimeTemplate,
        RussianLanguageIndex russian,
        Language resourceEnglish
    ) {
        return TranslationCoverage.isTranslated(
            russian.values().containsKey(key),
            runtimeTemplate,
            resourceEnglish.getOrDefault(key)
        );
    }

    record Result(boolean success, String message) {
        static Result failure(String message) {
            return new Result(false, message);
        }
    }

    private record Candidate(String uid, ItemStack stack, Set<String> sources) {
    }

    private record AuditReport(boolean success, int checked, int failures, JsonObject json) {
    }

    private record AuditEntry(
        String uid,
        String itemId,
        String descriptionId,
        String displayName,
        Set<String> discoveredFrom,
        Set<String> translationKeys,
        Set<String> missingRussianKeys,
        Set<String> sameAsEnglishKeys,
        Map<String, String> translationSources,
        AuditStatus status,
        String error
    ) {
        boolean failure() {
            return status.isFailure();
        }

        JsonObject json() {
            JsonObject value = new JsonObject();
            value.addProperty("uid", uid);
            value.addProperty("item_id", itemId);
            value.addProperty("description_id", descriptionId);
            value.addProperty("display_name", displayName);
            value.add("discovered_from", strings(discoveredFrom));
            value.add("translation_keys", strings(translationKeys));
            value.add("missing_russian_keys", strings(missingRussianKeys));
            value.add("same_as_english_keys", strings(sameAsEnglishKeys));

            JsonObject sources = new JsonObject();
            translationSources.forEach(sources::addProperty);
            value.add("translation_sources", sources);
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
