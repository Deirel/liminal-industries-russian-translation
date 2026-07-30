package ru.deirel.liminalindustries.translation.audit.layout;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import ru.deirel.liminalindustries.translation.audit.TranslationAuditIndex;
import vazkii.patchouli.client.book.BookCategory;
import vazkii.patchouli.client.book.BookEntry;
import vazkii.patchouli.client.book.BookPage;
import vazkii.patchouli.client.book.gui.BookTextRenderer;
import vazkii.patchouli.client.book.gui.GuiBook;
import vazkii.patchouli.client.book.gui.GuiBookCategory;
import vazkii.patchouli.client.book.gui.GuiBookEntry;
import vazkii.patchouli.client.book.gui.GuiBookLanding;
import vazkii.patchouli.client.book.gui.button.GuiButtonEntry;
import vazkii.patchouli.client.book.page.abstr.PageDoubleRecipe;
import vazkii.patchouli.client.book.page.abstr.PageWithText;
import vazkii.patchouli.client.book.text.Word;
import vazkii.patchouli.common.book.Book;
import vazkii.patchouli.common.book.BookRegistry;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

final class PatchouliLayoutAdapter {
    private static final double ENTRY_LABEL_SCALE = 0.5;
    private static final String ENGINE = "patchouli";

    List<LayoutScreen> screens() {
        Set<ResourceLocation> selected = selectedBooks();
        List<LayoutScreen> result = new ArrayList<>();
        BookRegistry.INSTANCE.books.values().stream()
            .filter(book -> selected.contains(book.id))
            .sorted(Comparator.comparing(book -> book.id.toString()))
            .forEach(book -> addBook(result, book));
        return List.copyOf(result);
    }

    void resetAfterAudit() {
        Set<ResourceLocation> selected = selectedBooks();
        BookRegistry.INSTANCE.books.values().stream()
            .filter(book -> selected.contains(book.id))
            .forEach(book -> {
                book.getContents().guiStack.clear();
                book.getContents().currentGui = null;
            });
    }

    private Set<ResourceLocation> selectedBooks() {
        return TranslationAuditIndex
            .screenRecords(ENGINE)
            .stream()
            .map(TranslationAuditIndex.ScreenRecord::bookId)
            .collect(java.util.stream.Collectors.toSet());
    }

    LayoutCapture capture(
        Minecraft minecraft,
        LayoutScreen target,
        Screen screen,
        String language
    ) {
        if (!(screen instanceof GuiBook gui)) {
            throw new IllegalStateException("Patchouli did not open a GuiBook screen");
        }
        double scale = field(gui, "scaleFactor", Float.class);
        int pageTop = gui instanceof GuiBookEntry ? 14 : 0;
        int pageHeight = gui instanceof GuiBookEntry ? 160 : 180;
        List<LayoutRegion> pages = List.of(
            region(
                gui,
                scale,
                "left",
                LayoutRegion.Kind.PAGE,
                15,
                pageTop,
                116,
                pageHeight
            ),
            region(
                gui,
                scale,
                "right",
                LayoutRegion.Kind.PAGE,
                141,
                pageTop,
                116,
                pageHeight
            )
        );
        List<LayoutRegion> scissors = List.of();
        List<LayoutRegion> text = captureText(gui, scale, target);
        List<LayoutRegion> controls = captureControls(gui, scale);
        List<LayoutRegion> missingContent = captureMissingContent(
            minecraft,
            gui,
            scale
        );
        return new LayoutCapture(
            ENGINE,
            target.book(),
            target.id(),
            target.resource(),
            target.entry(),
            target.page(),
            target.textSource(),
            language,
            minecraft.getWindow().getGuiScaledWidth(),
            minecraft.getWindow().getGuiScaledHeight(),
            minecraft.getWindow().getGuiScale(),
            text,
            pages,
            scissors,
            controls,
            missingContent
        );
    }

    private List<LayoutRegion> captureMissingContent(
        Minecraft minecraft,
        GuiBook gui,
        double scale
    ) {
        if (minecraft.level == null) {
            return List.of();
        }
        List<LayoutRegion> result = new ArrayList<>();
        for (BookPage page : activePages(gui)) {
            int pageNumber = field(page, "pageNum", Integer.class);
            String side = page.left < 136 ? "left" : "right";
            for (PatchouliRecipeReferences.MissingReference missing :
                missingRecipeReferences(minecraft, page)) {
                result.add(new LayoutRegion(
                    "missing-recipe-" + pageNumber + "-" + missing.recipe(),
                    LayoutRegion.Kind.TEXT,
                    side,
                    page.top,
                    (gui.bookLeft + page.left) * scale,
                    (gui.bookTop + page.top) * scale,
                    1,
                    1
                ));
            }
        }
        return List.copyOf(result);
    }

    private List<PatchouliRecipeReferences.MissingReference>
        missingRecipeReferences(Minecraft minecraft, BookPage page) {
        if (page instanceof PageDoubleRecipe<?>) {
            return PatchouliRecipeReferences.missingResolved(
                page.sourceObject,
                id -> minecraft.level.getRecipeManager().byKey(id).orElse(null),
                optionalField(page, "recipe1", Object.class),
                optionalField(page, "recipe2", Object.class)
            );
        }
        return PatchouliRecipeReferences.missing(
            page.sourceObject,
            id -> minecraft.level.getRecipeManager().byKey(id).isPresent()
        );
    }

    private void addBook(List<LayoutScreen> result, Book book) {
        result.add(screen(
            book,
            "landing",
            "<landing>",
            null,
            () -> new GuiBookLanding(book)
        ));
        book.getContents().categories.values().stream()
            .sorted(Comparator.comparing(category -> category.getId().toString()))
            .forEach(category -> result.add(screen(
                book,
                "category/" + category.getId(),
                category.getId().toString(),
                null,
                () -> new GuiBookCategory(book, category)
            )));
        book.getContents().entries.values().stream()
            .sorted(Comparator.comparing(entry -> entry.getId().toString()))
            .forEach(entry ->
                spreadTargets(entry.getPages().size()).forEach(target ->
                    result.add(screen(
                        book,
                        "entry/" + entry.getId() + "/" + target.firstPage(),
                        entry.getId().toString(),
                        target.firstPage(),
                        () -> new GuiBookEntry(book, entry, target.spread())
                    ))
                )
            );
    }

    static List<SpreadTarget> spreadTargets(int pageCount) {
        int spreads = Math.max(1, (pageCount + 1) / 2);
        List<SpreadTarget> result = new ArrayList<>(spreads);
        for (int spread = 0; spread < spreads; spread++) {
            result.add(new SpreadTarget(spread, spread * 2));
        }
        return List.copyOf(result);
    }

    private LayoutScreen screen(
        Book book,
        String suffix,
        String entry,
        Integer page,
        java.util.function.Supplier<Screen> factory
    ) {
        TranslationAuditIndex.ScreenRecord indexed = TranslationAuditIndex
            .screenRecords(ENGINE)
            .stream()
            .filter(record -> record.bookId().equals(book.id))
            .filter(record -> matches(record, book, suffix, entry))
            .filter(record -> page == null || record.page() == null || record.page().equals(page))
            .min(Comparator.comparingInt(record -> sourceRank(record, page, null)))
            .orElse(null);
        return new LayoutScreen(
            ENGINE,
            book.id.toString(),
            ENGINE + ":" + book.id + ":" + suffix,
            indexed == null ? "<runtime>" : indexed.resource(),
            entry,
            page,
            indexed == null
                ? "<runtime>"
                : indexed.textSourceType() + ":" + indexed.textSource(),
            factory
        );
    }

    private boolean matches(
        TranslationAuditIndex.ScreenRecord record,
        Book book,
        String suffix,
        String entry
    ) {
        if (suffix.equals("landing")) {
            return record.textSource().equals(book.landingText)
                || record.resource().endsWith("/book.json");
        }
        String path = ResourceLocation.tryParse(entry) == null
            ? entry
            : ResourceLocation.parse(entry).getPath();
        if (suffix.startsWith("category/")) {
            return record.resource().contains("/categories/" + path + ".json");
        }
        if (suffix.startsWith("entry/")) {
            return record.resource().contains("/entries/" + path + ".json");
        }
        return false;
    }

    private List<LayoutRegion> captureText(
        GuiBook gui,
        double scale,
        LayoutScreen target
    ) {
        List<LayoutRegion> result = new ArrayList<>();
        List<RendererContext> renderers = new ArrayList<>();
        findRenderers(gui, null, "screen", renderers);
        for (BookPage page : activePages(gui)) {
            if (page instanceof PageWithText textPage && !textPage.shouldRenderText()) {
                continue;
            }
            String side = page.left < 136 ? "left" : "right";
            findRenderers(page, page, side, renderers);
        }
        for (RendererContext context : renderers) {
            BookTextRenderer renderer = context.renderer();
            String rendererSource = context.owner() == null
                ? target.textSource()
                : indexedSource(
                    target,
                    field(context.owner(), "pageNum", Integer.class),
                    "/text"
                );
            @SuppressWarnings("unchecked")
            List<Word> words = field(renderer, "words", List.class);
            if (words.isEmpty()) {
                continue;
            }
            Word anchor = words.get(0);
            double rendererScale = field(renderer, "scale", Float.class);
            double ownerLeft = context.owner() == null ? 0 : context.owner().left;
            double ownerTop = context.owner() == null ? 0 : context.owner().top;
            int wordIndex = 0;
            for (Word word : words) {
                Component rendered = field(word, "text", Component.class);
                Component visibleText = withoutTrailingWhitespace(rendered);
                int width = gui.getMinecraft().font.width(
                    visibleText.copy().withStyle(gui.book.getFontStyle())
                );
                String id = "text-" + context.id() + "-" + wordIndex++;
                if (width <= 0) {
                    continue;
                }
                String page = context.owner() == null
                    ? (word.x < 136 ? "left" : "right")
                    : (context.owner().left < 136 ? "left" : "right");
                int line = (int) Math.round(
                    ownerTop + anchor.y + (word.y - anchor.y) * rendererScale
                );
                result.add(RenderedTextGeometry.region(
                    id,
                    page,
                    line,
                    gui.bookLeft,
                    gui.bookTop,
                    ownerLeft,
                    ownerTop,
                    anchor.x,
                    anchor.y,
                    word.x,
                    word.y,
                    width,
                    word.height,
                    rendererScale,
                    scale,
                    componentSource(rendered, rendererSource)
                ));
            }
        }
        captureWidgetText(gui, scale, target.textSource(), result);
        addTitles(gui, scale, target, result);
        return List.copyOf(result);
    }

    private Component withoutTrailingWhitespace(Component component) {
        String text = component.getString();
        String visibleText = RenderedTextGeometry.trimTrailingWhitespace(text);
        if (visibleText.length() == text.length()) {
            return component;
        }
        return Component.literal(visibleText).setStyle(component.getStyle());
    }

    private void captureWidgetText(
        GuiBook gui,
        double scale,
        String fallbackSource,
        List<LayoutRegion> result
    ) {
        int index = 0;
        for (GuiEventListener child : gui.children()) {
            if (!(child instanceof GuiButtonEntry button) || !button.visible) {
                continue;
            }
            Component rendered = button.getEntry().isLocked()
                ? Component.translatable("patchouli.gui.lexicon.locked")
                : button.getMessage();
            int width = gui.getMinecraft().font.width(rendered);
            double x = button.getX() + 12;
            String page = x - gui.bookLeft < 136 ? "left" : "right";
            result.add(new LayoutRegion(
                "entry-label-" + index++,
                LayoutRegion.Kind.TEXT,
                page,
                button.getY(),
                x * scale,
                button.getY() * scale,
                width * ENTRY_LABEL_SCALE * scale,
                9 * ENTRY_LABEL_SCALE * scale,
                componentSource(
                    rendered,
                    indexedEntryNameSource(button.getEntry(), fallbackSource)
                )
            ));
        }
    }

    private void addTitles(
        GuiBook gui,
        double scale,
        LayoutScreen target,
        List<LayoutRegion> result
    ) {
        for (BookPage page : activePages(gui)) {
            int pageNumber = field(page, "pageNum", Integer.class);
            Component rendered;
            if (pageNumber == 0 && gui instanceof GuiBookEntry entryScreen) {
                rendered = entryScreen.getEntry().getName();
            } else {
                String title = optionalField(page, "title", String.class);
                if (title == null || title.isBlank()) {
                    continue;
                }
                rendered = page.i18nText(title);
            }
            int width = gui.getMinecraft().font.width(rendered);
            if (width <= 0) {
                continue;
            }
            String side = page.left < 136 ? "left" : "right";
            double x = page.left + (116 - width) / 2.0;
            result.add(region(
                gui,
                scale,
                "title-" + side + "-" + pageNumber,
                LayoutRegion.Kind.TEXT,
                side,
                page.top,
                x,
                page.top,
                width,
                9,
                componentSource(
                    rendered,
                    indexedSource(
                        target,
                        pageNumber,
                        pageNumber == 0 ? "/name" : "/title"
                    )
                )
            ));
        }
    }

    private List<LayoutRegion> captureControls(GuiBook gui, double scale) {
        List<LayoutRegion> result = new ArrayList<>();
        int index = 0;
        for (GuiEventListener child : gui.children()) {
            if (!(child instanceof AbstractWidget widget) || !widget.visible) {
                continue;
            }
            if (widget instanceof GuiButtonEntry) {
                continue;
            }
            String page = widget.getX() - gui.bookLeft < 136 ? "left" : "right";
            result.add(new LayoutRegion(
                "control-" + index++,
                LayoutRegion.Kind.CONTROL,
                page,
                -1,
                widget.getX() * scale,
                widget.getY() * scale,
                widget.getWidth() * scale,
                widget.getHeight() * scale
            ));
        }
        return List.copyOf(result);
    }

    private List<BookPage> activePages(GuiBook gui) {
        if (!(gui instanceof GuiBookEntry)) {
            return List.of();
        }
        List<BookPage> result = new ArrayList<>();
        BookPage left = optionalField(gui, "leftPage", BookPage.class);
        BookPage right = optionalField(gui, "rightPage", BookPage.class);
        if (left != null) {
            result.add(left);
        }
        if (right != null) {
            result.add(right);
        }
        return result;
    }

    private void findRenderers(
        Object owner,
        BookPage page,
        String prefix,
        List<RendererContext> renderers
    ) {
        for (Class<?> type = owner.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (!BookTextRenderer.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                BookTextRenderer value = read(owner, field, BookTextRenderer.class);
                boolean seen = renderers.stream()
                    .anyMatch(context -> context.renderer() == value);
                if (value != null && !seen) {
                    renderers.add(new RendererContext(
                        value,
                        page,
                        prefix + "-" + field.getName()
                    ));
                }
            }
        }
    }

    private LayoutRegion region(
        GuiBook gui,
        double scale,
        String id,
        LayoutRegion.Kind kind,
        double x,
        double y,
        double width,
        double height
    ) {
        String page = x < 136 ? "left" : "right";
        return region(gui, scale, id, kind, page, -1, x, y, width, height);
    }

    private LayoutRegion region(
        GuiBook gui,
        double scale,
        String id,
        LayoutRegion.Kind kind,
        String page,
        int line,
        double x,
        double y,
        double width,
        double height
    ) {
        return region(
            gui,
            scale,
            id,
            kind,
            page,
            line,
            x,
            y,
            width,
            height,
            null
        );
    }

    private LayoutRegion region(
        GuiBook gui,
        double scale,
        String id,
        LayoutRegion.Kind kind,
        String page,
        int line,
        double x,
        double y,
        double width,
        double height,
        String source
    ) {
        return new LayoutRegion(
            id,
            kind,
            page,
            line,
            (gui.bookLeft + x) * scale,
            (gui.bookTop + y) * scale,
            width * scale,
            height * scale,
            source
        );
    }

    private String componentSource(Component component, String fallback) {
        if (component.getContents() instanceof TranslatableContents translatable) {
            return "translation_key:" + translatable.getKey();
        }
        for (Component sibling : component.getSiblings()) {
            String source = componentSource(sibling, null);
            if (source != null) {
                return source;
            }
        }
        return fallback;
    }

    private String indexedEntryNameSource(BookEntry entry, String fallback) {
        return TranslationAuditIndex.screenRecords(ENGINE).stream()
            .filter(record -> record.bookId().equals(entry.getBook().id))
            .filter(record -> sameEntry(record.entry(), entry.getId().toString()))
            .min(Comparator.comparingInt(record -> sourceRank(record, null, "/name")))
            .map(record -> record.textSourceType() + ":" + record.textSource())
            .orElse(fallback);
    }

    private String indexedSource(
        LayoutScreen target,
        int page,
        String preferredSuffix
    ) {
        ResourceLocation bookId = ResourceLocation.parse(target.book());
        return TranslationAuditIndex.screenRecords(ENGINE).stream()
            .filter(record -> record.bookId().equals(bookId))
            .filter(record -> sameEntry(record.entry(), target.entry()))
            .filter(record -> record.page() == null || record.page() == page)
            .min(Comparator.comparingInt(
                record -> sourceRank(record, page, preferredSuffix)
            ))
            .map(record -> record.textSourceType() + ":" + record.textSource())
            .orElse(target.textSource());
    }

    private boolean sameEntry(String indexed, String runtime) {
        ResourceLocation parsed = ResourceLocation.tryParse(runtime);
        String path = parsed == null ? runtime : parsed.getPath();
        return indexed.equals(path) || indexed.equals(runtime);
    }

    private int sourceRank(
        TranslationAuditIndex.ScreenRecord record,
        Integer page,
        String preferredSuffix
    ) {
        int rank = 0;
        if (preferredSuffix != null
            && !record.textSource().endsWith(preferredSuffix)) {
            rank += 100;
        }
        if (page != null && !page.equals(record.page())) {
            rank += 10;
        }
        return rank;
    }

    private static <T> T field(Object owner, String name, Class<T> type) {
        T value = optionalField(owner, name, type);
        if (value == null) {
            throw new IllegalStateException(
                "Missing Patchouli layout field " + owner.getClass().getName() + "." + name
            );
        }
        return value;
    }

    private static <T> T optionalField(Object owner, String name, Class<T> type) {
        for (Class<?> cursor = owner.getClass(); cursor != null; cursor = cursor.getSuperclass()) {
            try {
                Field field = cursor.getDeclaredField(name);
                return read(owner, field, type);
            } catch (NoSuchFieldException ignored) {
                // Continue through the hierarchy.
            }
        }
        return null;
    }

    private static <T> T read(Object owner, Field field, Class<T> type) {
        try {
            field.setAccessible(true);
            return type.cast(field.get(owner));
        } catch (ReflectiveOperationException | ClassCastException exception) {
            throw new IllegalStateException(
                "Could not inspect Patchouli layout field " + field,
                exception
            );
        }
    }

    private record RendererContext(
        BookTextRenderer renderer,
        BookPage owner,
        String id
    ) {
    }

    record SpreadTarget(int spread, int firstPage) {
    }
}
