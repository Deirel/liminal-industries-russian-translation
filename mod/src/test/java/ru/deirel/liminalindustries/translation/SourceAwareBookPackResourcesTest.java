package ru.deirel.liminalindustries.translation;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.resources.IoSupplier;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SourceAwareBookPackResourcesTest {
    @Test
    void missingUpstreamResourceDoesNotExposeStaleBaseline() throws IOException {
        ResourceLocation output = ResourceLocation.fromNamespaceAndPath(
            "example",
            "ru_ru/book.json"
        );
        BookTranslationIndex index = BookTranslationIndex.parse(
            new ByteArrayInputStream(bytes("""
                {
                  "schema": 1,
                  "resources": [{
                    "format": "json",
                    "source": {
                      "namespace": "example",
                      "path": "en_us/book.json",
                      "sha256": "sha256:old"
                    },
                    "output": {
                      "namespace": "example",
                      "path": "ru_ru/book.json"
                    },
                    "fields": [{
                      "id": "title",
                      "source": "Old",
                      "pointer": "/title",
                      "translation": "Старое"
                    }]
                  }]
                }
                """))
        );
        SourceAwareBookPackResources resources =
            new SourceAwareBookPackResources(
                new OneResourcePack(output, bytes("{\"title\":\"Старое\"}")),
                index,
                (ignored, hash) ->
                    BookSourceResolver.Resolution.missing("not installed")
            );

        assertNull(resources.getResource(PackType.CLIENT_RESOURCES, output));
        assertEquals(
            LiminalIndustriesTranslationMod.MOD_ID + "_baseline",
            resources.packId()
        );
        AtomicInteger listed = new AtomicInteger();
        resources.listResources(
            PackType.CLIENT_RESOURCES,
            "example",
            "",
            (location, supplier) -> listed.incrementAndGet()
        );
        assertEquals(0, listed.get());
        assertEquals(
            "MISSING_SOURCE",
            BookTranslationDiagnostics.Entry.class.cast(
                new com.google.gson.Gson().fromJson(
                    BookTranslationDiagnostics.reportJson(),
                    BookTranslationDiagnostics.Entry[].class
                )[0]
            ).status()
        );
        BookTranslationDiagnostics.clear();
    }

    private record OneResourcePack(
        ResourceLocation location,
        byte[] value
    ) implements PackResources {
        @Override
        public IoSupplier<InputStream> getRootResource(String... path) {
            return null;
        }

        @Override
        public IoSupplier<InputStream> getResource(
            PackType type,
            ResourceLocation requested
        ) {
            return location.equals(requested)
                ? () -> new ByteArrayInputStream(value)
                : null;
        }

        @Override
        public void listResources(
            PackType type,
            String namespace,
            String path,
            ResourceOutput output
        ) {
            output.accept(location, () -> new ByteArrayInputStream(value));
        }

        @Override
        public Set<String> getNamespaces(PackType type) {
            return Set.of(location.getNamespace());
        }

        @Override
        public <T> T getMetadataSection(
            MetadataSectionSerializer<T> serializer
        ) {
            return null;
        }

        @Override
        public String packId() {
            return "test";
        }

        @Override
        public void close() {
        }
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
