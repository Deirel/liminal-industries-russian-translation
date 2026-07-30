package ru.deirel.liminalindustries.translation.audit.layout;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PatchouliLayoutAdapterTest {
    @Test
    void pairsEachSpreadWithItsFirstPage() {
        assertEquals(
            List.of(
                new PatchouliLayoutAdapter.SpreadTarget(0, 0),
                new PatchouliLayoutAdapter.SpreadTarget(1, 2),
                new PatchouliLayoutAdapter.SpreadTarget(2, 4),
                new PatchouliLayoutAdapter.SpreadTarget(3, 6)
            ),
            PatchouliLayoutAdapter.spreadTargets(7)
        );
    }
}
