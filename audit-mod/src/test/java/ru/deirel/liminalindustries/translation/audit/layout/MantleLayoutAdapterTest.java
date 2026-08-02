package ru.deirel.liminalindustries.translation.audit.layout;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import ru.deirel.liminalindustries.translation.audit.TranslationAuditIndex;
import slimeknights.mantle.client.book.data.PageData;
import slimeknights.mantle.client.book.data.SectionData;
import slimeknights.mantle.client.book.repository.BookRepository;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MantleLayoutAdapterTest {
    @Test
    void discoversTinkersBooksWithoutTranslationScreenRecords() {
        assertEquals(
            Set.of(
                ResourceLocation.parse("tconstruct:materials_and_you"),
                ResourceLocation.parse("tconstruct:mighty_smelting"),
                ResourceLocation.parse("tconstruct:puny_smelting"),
                ResourceLocation.parse("tconstruct:tinkers_gadgetry"),
                ResourceLocation.parse("tconstruct:fantastic_foundry"),
                ResourceLocation.parse("tconstruct:encyclopedia")
            ),
            MantleLayoutAdapter.selectedBooks(List.of())
        );
    }

    @Test
    void keepsIndexedMantleBooksAlongsideTinkersBooks() {
        ResourceLocation customBook = ResourceLocation.parse("example:guide");

        assertTrue(MantleLayoutAdapter.selectedBooks(List.of(record(
            customBook,
            "assets/example/book/guide/ru_ru/intro.json",
            "intro"
        ))).contains(customBook));
    }

    @Test
    void keepsGroupAndGeneratedPageRecordsForSourceAttribution() {
        ResourceLocation book = ResourceLocation.parse("tconstruct:encyclopedia");
        PageData page = new PageData();
        page.data = "tools/small.json";
        page.name = "tconstruct.pickaxe";
        TranslationAuditIndex.ScreenRecord group = record(
            book,
            "assets/tconstruct/book/encyclopedia/ru_ru/tools/small.json",
            "small"
        );
        TranslationAuditIndex.ScreenRecord generated = record(
            book,
            "assets/tconstruct/book/encyclopedia/ru_ru/tools/small/tconstruct_pickaxe.json",
            "tconstruct_pickaxe"
        );
        TranslationAuditIndex.ScreenRecord unrelated = record(
            book,
            "assets/tconstruct/book/encyclopedia/ru_ru/tools/small/tconstruct_mattock.json",
            "tconstruct_mattock"
        );

        assertEquals(
            List.of(group, generated),
            MantleLayoutAdapter.matchingPageRecords(
                List.of(group, generated, unrelated),
                book,
                page
            )
        );
    }

    @Test
    void indexedResourceWinsOverRuntimeResourceForRescriptedAttribution() {
        ResourceLocation book = ResourceLocation.parse("tconstruct:encyclopedia");
        TranslationAuditIndex.ScreenRecord indexed = record(
            book,
            "assets/tconstruct/book/encyclopedia/ru_ru/tools/small.json",
            "small"
        );

        assertEquals(
            indexed.resource(),
            MantleLayoutAdapter.preferredResource(
                List.of(indexed),
                "assets/tconstruct/book/encyclopedia/en_us/tools/small.json"
            )
        );
    }

    @Test
    void runtimeResourceFillsOriginalAttributionWithoutAnIndex() {
        PageData page = new PageData();
        page.data = "tools/small.json";
        page.source = new FixedRepository(
            ResourceLocation.parse(
                "tconstruct:book/encyclopedia/ru_ru/tools/small.json"
            )
        );

        assertEquals(
            "assets/tconstruct/book/encyclopedia/ru_ru/tools/small.json",
            MantleLayoutAdapter.preferredResource(
                List.of(),
                MantleLayoutAdapter.runtimePageResource(page)
            )
        );
    }

    @Test
    void matchesGeneratedPageNamesToIndexedResourceEntries() {
        assertTrue(MantleLayoutAdapter.matchesPageName(
            "tconstruct_luck",
            "general.tconstruct.luck"
        ));
        assertTrue(MantleLayoutAdapter.matchesPageName(
            "tconstruct_luck",
            "tconstruct_luck"
        ));
        assertFalse(MantleLayoutAdapter.matchesPageName(
            "tconstruct_luck",
            "general.tconstruct.expanded"
        ));
    }

    @Test
    void identifiesOneUnescapedMantleTranslationKey() {
        assertEquals(
            "translation_key:tconstruct.book.research",
            MantleLayoutAdapter.translationKeySource(
                "$(tconstruct.book.research)$"
            )
        );
        assertEquals(
            "translation_key:tconstruct.book.research",
            MantleLayoutAdapter.translationKeySource(
                "- $(tconstruct.book.research)$"
            )
        );
        assertEquals(null, MantleLayoutAdapter.translationKeySource(
            "$(first)$ and $(second)$"
        ));
        assertEquals(null, MantleLayoutAdapter.translationKeySource(
            "$$(escaped)$$"
        ));
    }

    @Test
    void reverseIndexesOnlyUniqueRuntimeTranslations() {
        assertEquals(
            Map.of("Уникальный текст", "book.unique"),
            MantleLayoutAdapter.uniqueTranslationKeys(Map.of(
                "book.unique", "Уникальный текст",
                "book.duplicate.first", "Общий текст",
                "book.duplicate.second", "Общий текст"
            ))
        );
    }

    @Test
    void usesResolvedResourceAsLogicalPageIdentity() {
        assertEquals(
            "mantle:tconstruct:encyclopedia:resource/"
                + "assets/tconstruct/book/encyclopedia/<language>/tools/pickaxe.json",
            MantleLayoutAdapter.resourceLogicalPage(
                ResourceLocation.parse("tconstruct:encyclopedia"),
                "assets/tconstruct/book/encyclopedia/ru_ru/tools/pickaxe.json"
            )
        );
        assertEquals(
            MantleLayoutAdapter.resourceLogicalPage(
                ResourceLocation.parse("tconstruct:encyclopedia"),
                "assets/tconstruct/book/encyclopedia/en_us/tools/pickaxe.json"
            ),
            MantleLayoutAdapter.resourceLogicalPage(
                ResourceLocation.parse("tconstruct:encyclopedia"),
                "assets/tconstruct/book/encyclopedia/ru_ru/tools/pickaxe.json"
            )
        );
    }

    @Test
    void pairsRuntimeSpreadsWithTheirFirstLogicalPage() {
        assertEquals(
            List.of(
                new MantleLayoutAdapter.SpreadTarget(0, 0),
                new MantleLayoutAdapter.SpreadTarget(1, 1),
                new MantleLayoutAdapter.SpreadTarget(2, 3),
                new MantleLayoutAdapter.SpreadTarget(3, 5)
            ),
            MantleLayoutAdapter.spreadTargets(6)
        );
    }

    @Test
    void emptyBookHasNoSpreadTargets() {
        assertEquals(List.of(), MantleLayoutAdapter.spreadTargets(0));
    }

    @Test
    void attributesBothDisplayedPagesInsteadOfMantlesStaleRightPageGetter() {
        assertEquals(-1, MantleLayoutAdapter.displayedPage(0, 0));
        assertEquals(0, MantleLayoutAdapter.displayedPage(0, 1));
        assertEquals(9, MantleLayoutAdapter.displayedPage(5, 0));
        assertEquals(10, MantleLayoutAdapter.displayedPage(5, 1));
    }

    @Test
    void locatesTransformerPageWithoutParentReference() {
        SectionData section = new SectionData();
        section.name = "intro";
        PageData page = new PageData();
        page.name = "first";
        section.pages.add(page);

        assertEquals(
            "intro.first",
            MantleLayoutAdapter.pageLocation(page, List.of(section))
        );
    }

    @Test
    void usesMantlePageMarginAsHorizontalRenderingSlopOnly() {
        LayoutRegion bounds = MantleLayoutAdapter.withHorizontalRenderingSlop(
            new LayoutRegion(
                "page",
                LayoutRegion.Kind.PAGE,
                "left",
                -1,
                10,
                20,
                100,
                80
            )
        );

        assertTrue(bounds.contains(textAt(2, 20, 116, 9)));
        assertFalse(bounds.contains(textAt(2, 20, 117, 9)));
        assertFalse(bounds.contains(textAt(10, 92, 100, 9)));
    }

    private static LayoutRegion textAt(
        double x,
        double y,
        double width,
        double height
    ) {
        return new LayoutRegion(
            "text",
            LayoutRegion.Kind.TEXT,
            "left",
            0,
            x,
            y,
            width,
            height
        );
    }

    private static TranslationAuditIndex.ScreenRecord record(
        ResourceLocation book,
        String resource,
        String entry
    ) {
        return new TranslationAuditIndex.ScreenRecord(
            "mantle",
            book,
            resource,
            entry,
            null,
            "json_pointer",
            "/text/0/text",
            "English source"
        );
    }

    private static final class FixedRepository extends BookRepository {
        private final ResourceLocation location;

        private FixedRepository(ResourceLocation location) {
            this.location = location;
        }

        @Override
        public List<SectionData> getSections() {
            return List.of();
        }

        @Override
        public ResourceLocation getResourceLocation(String path, boolean safe) {
            return location;
        }

        @Override
        public java.util.Optional<net.minecraft.server.packs.resources.Resource>
            getLocation(ResourceLocation requested) {
            return java.util.Optional.empty();
        }

        @Override
        public String resourceToString(
            net.minecraft.server.packs.resources.Resource resource,
            boolean stripComments
        ) {
            return "";
        }
    }
}
