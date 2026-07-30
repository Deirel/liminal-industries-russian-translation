package ru.deirel.liminalindustries.translation.audit.layout;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
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
import vazkii.patchouli.client.book.page.abstr.PageDoubleRecipe;
import vazkii.patchouli.client.book.text.Word;
import vazkii.patchouli.common.book.Book;
import vazkii.patchouli.common.book.BookRegistry;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public final class PatchouliLayoutAdapter implements LayoutAdapter {
    @Override
    public String engine() {
        return "patchouli";
    }

    @Override
    public List<LayoutScreen> screens(Minecraft minecraft) {
        Set<ResourceLocation> selected = TranslationAuditIndex
            .screenRecords(engine())
            .stream()
            .map(TranslationAuditIndex.ScreenRecord::bookId)
            .collect(java.util.stream.Collectors.toSet());
        List<LayoutScreen> result = new ArrayList<>();
        BookRegistry.INSTANCE.books.values().stream()
            .filter(book -> selected.contains(book.id))
            .sorted(Comparator.comparing(book -> book.id.toString()))
            .forEach(book -> addBook(result, book));
        return List.copyOf(result);
    }

    @Override
    public LayoutCapture capture(
        Minecraft minecraft,
        LayoutScreen target,
        Screen screen,
        String language
    ) {
        if (!(screen instanceof GuiBook gui)) {
            throw new IllegalStateException("Patchouli did not open a GuiBook screen");
        }
        double scale = field(gui, "scaleFactor", Float.class);
        List<LayoutRegion> pages = List.of(
            region(gui, scale, "left", LayoutRegion.Kind.PAGE, 15, 18, 116, 156),
            region(gui, scale, "right", LayoutRegion.Kind.PAGE, 141, 18, 116, 156)
        );
        List<LayoutRegion> scissors = List.of(
            region(gui, scale, "left", LayoutRegion.Kind.SCISSOR, 15, 18, 116, 156),
            region(gui, scale, "right", LayoutRegion.Kind.SCISSOR, 141, 18, 116, 156)
        );
        List<LayoutRegion> text = captureText(gui, scale);
        List<LayoutRegion> controls = captureControls(gui, scale);
        List<LayoutRegion> missingContent = captureMissingContent(
            minecraft,
            gui,
            scale
        );
        return new LayoutCapture(
            engine(),
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
                optionalField(page, "recipeId", ResourceLocation.class),
                optionalField(page, "recipe1", Object.class),
                optionalField(page, "recipe2Id", ResourceLocation.class),
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
            .forEach(entry -> {
                int spreads = Math.max(1, (entry.getPages().size() + 1) / 2);
                for (int spread = 0; spread < spreads; spread++) {
                    int firstPage = spread * 2;
                    result.add(screen(
                        book,
                        "entry/" + entry.getId() + "/" + firstPage,
                        entry.getId().toString(),
                        firstPage,
                        () -> new GuiBookEntry(book, entry, firstPage)
                    ));
                }
            });
    }

    private LayoutScreen screen(
        Book book,
        String suffix,
        String entry,
        Integer page,
        java.util.function.Supplier<Screen> factory
    ) {
        TranslationAuditIndex.ScreenRecord indexed = TranslationAuditIndex
            .screenRecords(engine())
            .stream()
            .filter(record -> record.bookId().equals(book.id))
            .filter(record -> matches(record, book, suffix, entry))
            .filter(record -> page == null || record.page() == null || record.page().equals(page))
            .findFirst()
            .orElse(null);
        return new LayoutScreen(
            engine(),
            book.id.toString(),
            engine() + ":" + book.id + ":" + suffix,
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

    private List<LayoutRegion> captureText(GuiBook gui, double scale) {
        List<LayoutRegion> result = new ArrayList<>();
        List<BookTextRenderer> renderers = new ArrayList<>();
        findRenderers(gui, renderers);
        activePages(gui).forEach(page -> findRenderers(page, renderers));
        int rendererIndex = 0;
        for (BookTextRenderer renderer : renderers) {
            @SuppressWarnings("unchecked")
            List<Word> words = field(renderer, "words", List.class);
            int wordIndex = 0;
            for (Word word : words) {
                String page = word.x < 136 ? "left" : "right";
                result.add(region(
                    gui,
                    scale,
                    "text-" + rendererIndex + "-" + wordIndex++,
                    LayoutRegion.Kind.TEXT,
                    page,
                    word.y,
                    word.x,
                    word.y,
                    word.width,
                    word.height
                ));
            }
            rendererIndex++;
        }
        addTitles(gui, scale, result);
        return List.copyOf(result);
    }

    private void addTitles(GuiBook gui, double scale, List<LayoutRegion> result) {
        for (BookPage page : activePages(gui)) {
            String title = optionalField(page, "title", String.class);
            if (title == null || title.isBlank()) {
                continue;
            }
            Component rendered = page.i18nText(title);
            int width = gui.getMinecraft().font.width(rendered);
            String side = page.left < 136 ? "left" : "right";
            double x = page.left + (116 - width) / 2.0;
            result.add(region(
                gui,
                scale,
                "title-" + side,
                LayoutRegion.Kind.TEXT,
                side,
                page.top,
                x,
                page.top,
                width,
                9
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
        List<BookTextRenderer> renderers
    ) {
        for (Class<?> type = owner.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (!BookTextRenderer.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                BookTextRenderer value = read(owner, field, BookTextRenderer.class);
                if (value != null && !renderers.contains(value)) {
                    renderers.add(value);
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
        return new LayoutRegion(
            id,
            kind,
            page,
            line,
            (gui.bookLeft + x) * scale,
            (gui.bookTop + y) * scale,
            width * scale,
            height * scale
        );
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
}
