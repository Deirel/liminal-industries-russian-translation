package ru.deirel.liminalindustries.translation;

import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.flag.FeatureFlagSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TranslationPackOrderTest {
    private static final ResourceLocation LANGUAGE_FILE =
        ResourceLocation.fromNamespaceAndPath(
            "beachparty",
            "lang/ru_ru.json"
        );
    private static final String BEACH_CHAIR =
        "block.beachparty.beach_chair";

    @BeforeAll
    static void initializeGameVersion() {
        SharedConstants.tryDetectVersion();
    }

    @Test
    void baselineOverridesConflictingModTranslation() throws IOException {
        List<Pack> ordered =
            TranslationPackOrder.placeBaselineAfterModResources(List.of(
                languagePack(
                    LiminalIndustriesTranslationMod.BASELINE_PACK_ID,
                    "Пляжное кресло"
                ),
                languagePack("mod_resources", "Beach Chair")
            ));

        assertEquals(
            List.of(
                "mod_resources",
                LiminalIndustriesTranslationMod.BASELINE_PACK_ID
            ),
            ordered.stream().map(Pack::getId).toList()
        );
        assertEquals(
            new EffectiveTranslation(
                "Пляжное кресло",
                LiminalIndustriesTranslationMod.BASELINE_PACK_ID
            ),
            effectiveTranslation(ordered)
        );
    }

    @Test
    void explicitUserPackStillOverridesBaseline() throws IOException {
        List<Pack> ordered =
            TranslationPackOrder.placeBaselineAfterModResources(List.of(
                languagePack(
                    LiminalIndustriesTranslationMod.BASELINE_PACK_ID,
                    "Пляжное кресло"
                ),
                languagePack("mod_resources", "Beach Chair"),
                languagePack("file/user", "Пользовательское кресло")
            ));

        assertEquals(
            List.of(
                "mod_resources",
                LiminalIndustriesTranslationMod.BASELINE_PACK_ID,
                "file/user"
            ),
            ordered.stream().map(Pack::getId).toList()
        );
        assertEquals(
            new EffectiveTranslation(
                "Пользовательское кресло",
                "file/user"
            ),
            effectiveTranslation(ordered)
        );
    }

    private static Pack languagePack(String id, String translation) {
        return Pack.create(
            id,
            Component.literal(id),
            false,
            ignored -> new LanguagePackResources(id, translation),
            new Pack.Info(
                Component.literal(id),
                15,
                FeatureFlagSet.of()
            ),
            PackType.CLIENT_RESOURCES,
            Pack.Position.BOTTOM,
            false,
            PackSource.BUILT_IN
        );
    }

    private static EffectiveTranslation effectiveTranslation(
        List<Pack> packs
    ) throws IOException {
        try (MultiPackResourceManager manager =
                 new MultiPackResourceManager(
                     PackType.CLIENT_RESOURCES,
                     packs.stream().map(Pack::open).toList()
                 )) {
            String value = null;
            String source = null;
            for (Resource resource : manager.getResourceStack(LANGUAGE_FILE)) {
                var language = JsonParser.parseReader(
                    resource.openAsReader()
                ).getAsJsonObject();
                if (language.has(BEACH_CHAIR)) {
                    value = language.get(BEACH_CHAIR).getAsString();
                    source = resource.sourcePackId();
                }
            }
            return new EffectiveTranslation(value, source);
        }
    }

    private record EffectiveTranslation(String value, String source) {
    }

    private static final class LanguagePackResources
        implements PackResources {
        private final String id;
        private final byte[] language;

        private LanguagePackResources(String id, String translation) {
            this.id = id;
            this.language = (
                "{\"" + BEACH_CHAIR + "\":\"" + translation + "\"}"
            ).getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public IoSupplier<InputStream> getRootResource(String... path) {
            return null;
        }

        @Override
        public IoSupplier<InputStream> getResource(
            PackType type,
            ResourceLocation location
        ) {
            if (type != PackType.CLIENT_RESOURCES
                || !LANGUAGE_FILE.equals(location)) {
                return null;
            }
            return () -> new ByteArrayInputStream(language);
        }

        @Override
        public void listResources(
            PackType type,
            String namespace,
            String path,
            ResourceOutput output
        ) {
            if (type == PackType.CLIENT_RESOURCES
                && LANGUAGE_FILE.getNamespace().equals(namespace)
                && LANGUAGE_FILE.getPath().startsWith(path)) {
                output.accept(
                    LANGUAGE_FILE,
                    () -> new ByteArrayInputStream(language)
                );
            }
        }

        @Override
        public Set<String> getNamespaces(PackType type) {
            return type == PackType.CLIENT_RESOURCES
                ? Set.of(LANGUAGE_FILE.getNamespace())
                : Set.of();
        }

        @Override
        public <T> T getMetadataSection(
            MetadataSectionSerializer<T> serializer
        ) {
            return null;
        }

        @Override
        public String packId() {
            return id;
        }

        @Override
        public boolean isBuiltin() {
            return true;
        }

        @Override
        public void close() {
        }
    }
}
