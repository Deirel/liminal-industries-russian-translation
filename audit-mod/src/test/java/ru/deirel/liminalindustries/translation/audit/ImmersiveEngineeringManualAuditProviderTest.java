package ru.deirel.liminalindustries.translation.audit;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ImmersiveEngineeringManualAuditProviderTest {
    @Test
    void readLinesIgnoresTrailingBlankLines() {
        ImmersiveEngineeringManualAuditProvider provider =
            new ImmersiveEngineeringManualAuditProvider();
        Resource resource = new Resource(
            (PackResources) null,
            () -> new ByteArrayInputStream(
                "Title\r\nBody\r\n\r\n".getBytes(StandardCharsets.UTF_8)
            )
        );

        assertEquals(
            List.of("Title", "Body"),
            provider.readLines(
                resource,
                ResourceLocation.fromNamespaceAndPath(
                    "immersiveengineering",
                    "manual/ru_ru/example.txt"
                )
            )
        );
    }
}
