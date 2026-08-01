package ru.deirel.liminalindustries.translation;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.resources.IoSupplier;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class SourceAwareBookPackResources implements PackResources {
    private final PackResources delegate;
    private final BookTranslationIndex index;
    private final BookSourceResolver sources;
    private final Map<ResourceLocation, Optional<byte[]>> generated =
        new ConcurrentHashMap<>();

    SourceAwareBookPackResources(
        PackResources delegate,
        BookTranslationIndex index,
        BookSourceResolver sources
    ) {
        this.delegate = delegate;
        this.index = index;
        this.sources = sources;
    }

    @Override
    public IoSupplier<InputStream> getRootResource(String... path) {
        return delegate.getRootResource(path);
    }

    @Override
    public IoSupplier<InputStream> getResource(
        PackType type,
        ResourceLocation location
    ) {
        BookTranslationIndex.Rule rule = type == PackType.CLIENT_RESOURCES
            ? index.rule(location)
            : null;
        if (rule == null) {
            return delegate.getResource(type, location);
        }
        Optional<byte[]> resolved = generated.computeIfAbsent(
            location,
            ignored -> Optional.ofNullable(resolve(type, rule))
        );
        return resolved.map(SourceAwareBookPackResources::bytes).orElse(null);
    }

    private byte[] resolve(PackType type, BookTranslationIndex.Rule rule) {
        String resource = rule.output().toString();
        BookSourceResolver.Resolution source = sources.resolve(
            rule.source(),
            rule.sourceSha256()
        );
        if (!source.found()
            && rule.format() == BookTranslationIndex.Format.LANGUAGE_JSON) {
            source = BookSourceResolver.Resolution.found("{}".getBytes(
                java.nio.charset.StandardCharsets.UTF_8
            ));
        } else if (!source.found()) {
            BookTranslationDiagnostics.record(
                new BookTranslationDiagnostics.Entry(
                    resource,
                    "MISSING_SOURCE",
                    0,
                    rule.fieldIds(),
                    source.error()
                )
            );
            LiminalIndustriesTranslationMod.LOGGER.warn(
                "Book translation {} skipped: {}",
                resource,
                source.error()
            );
            return null;
        }
        try {
            IoSupplier<InputStream> baselineSupplier = delegate.getResource(
                type,
                rule.output()
            );
            byte[] baseline = baselineSupplier == null
                ? null
                : read(baselineSupplier);
            BookTranslationAdapter.Result result =
                BookTranslationAdapter.adapt(rule, source.bytes(), baseline);
            String status = result.exactSource()
                ? "EXACT"
                : result.skippedFieldIds().isEmpty()
                    ? "ADAPTED"
                    : "SOURCE_CHANGED";
            BookTranslationDiagnostics.record(
                new BookTranslationDiagnostics.Entry(
                    resource,
                    status,
                    result.translatedFields(),
                    result.skippedFieldIds(),
                    result.exactSource()
                        ? "source fingerprint matched"
                        : "source-aware field merge"
                )
            );
            if (!result.skippedFieldIds().isEmpty()) {
                LiminalIndustriesTranslationMod.LOGGER.warn(
                    "Book translation {}: SOURCE_CHANGED, translated {}, skipped {} fields",
                    resource,
                    result.translatedFields(),
                    result.skippedFieldIds().size()
                );
            }
            return result.bytes();
        } catch (IOException | RuntimeException exception) {
            BookTranslationDiagnostics.record(
                new BookTranslationDiagnostics.Entry(
                    resource,
                    "INVALID_SOURCE",
                    0,
                    rule.fieldIds(),
                    exception.getMessage()
                )
            );
            LiminalIndustriesTranslationMod.LOGGER.warn(
                "Book translation {} skipped because the current source is invalid",
                resource,
                exception
            );
            return null;
        }
    }

    @Override
    public void listResources(
        PackType type,
        String namespace,
        String path,
        ResourceOutput output
    ) {
        delegate.listResources(type, namespace, path, (location, supplier) -> {
            if (index.rule(location) == null) {
                output.accept(location, supplier);
                return;
            }
            IoSupplier<InputStream> adapted = getResource(type, location);
            if (adapted != null) {
                output.accept(location, adapted);
            }
        });
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        return delegate.getNamespaces(type);
    }

    @Override
    public <T> T getMetadataSection(
        MetadataSectionSerializer<T> serializer
    ) throws IOException {
        return delegate.getMetadataSection(serializer);
    }

    @Override
    public String packId() {
        return LiminalIndustriesTranslationMod.BASELINE_PACK_ID;
    }

    @Override
    public boolean isBuiltin() {
        return delegate.isBuiltin();
    }

    @Override
    public void close() {
        delegate.close();
    }

    private static byte[] read(IoSupplier<InputStream> supplier)
        throws IOException {
        try (InputStream stream = supplier.get()) {
            return stream.readAllBytes();
        }
    }

    private static IoSupplier<InputStream> bytes(byte[] value) {
        return () -> new ByteArrayInputStream(value);
    }
}
