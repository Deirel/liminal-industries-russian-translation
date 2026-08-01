package ru.deirel.liminalindustries.translation.audit.layout;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientAdvancements;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import net.minecraftforge.registries.ForgeRegistries;
import ru.deirel.liminalindustries.translation.audit.TranslationAuditIndex;
import slimeknights.mantle.client.book.data.BookData;
import slimeknights.mantle.client.book.data.PageData;
import slimeknights.mantle.client.book.data.SectionData;
import slimeknights.mantle.client.book.data.element.TextComponentData;
import slimeknights.mantle.client.book.data.element.TextData;
import slimeknights.mantle.client.screen.book.BookScreen;
import slimeknights.mantle.client.screen.book.TextComponentDataRenderer;
import slimeknights.mantle.client.screen.book.TextDataRenderer;
import slimeknights.mantle.client.screen.book.element.BookElement;
import slimeknights.mantle.client.screen.book.element.TextComponentElement;
import slimeknights.mantle.client.screen.book.element.TextElement;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

final class MantleLayoutAdapter implements LayoutEngineAdapter {
    private static final String ENGINE = "mantle";
    private static final String ADVANCEMENT_LISTENER_FIELD = "f_104391_";
    private static final double HORIZONTAL_RENDERING_SLOP = BookScreen.PAGE_MARGIN;
    private static final Pattern TRANSLATION_KEY = Pattern.compile(
        "(?<!\\$)\\$\\(([^)]+)\\)\\$(?!\\$)"
    );
    private static final Field TEXT_LINEBREAK = publicField(
        TextData.class,
        "linebreak"
    );
    private static final Field COMPONENT_LINEBREAK = publicField(
        TextComponentData.class,
        "linebreak"
    );

    private final List<TranslationAuditIndex.ScreenRecord> records =
        TranslationAuditIndex.screenRecords(ENGINE);
    private final BookLayoutTranslationIndex translations =
        BookLayoutTranslationIndex.load(MantleLayoutAdapter.class);
    private final Set<String> translationKeys =
        TranslationAuditIndex.languageTranslationKeys();
    private final Map<ResourceLocation, BookData> loadedBooks = new LinkedHashMap<>();
    private final Map<String, Optional<JsonElement>> runtimeResources =
        new LinkedHashMap<>();
    private Language indexedLanguage;
    private Map<String, String> translationKeysByText = Map.of();
    private ClientAdvancements.Listener previousAdvancementListener;
    private boolean advancementListenerCaptured;

    @Override
    public String engine() {
        return ENGINE;
    }

    @Override
    public double renderingTolerance() {
        return 1;
    }

    @Override
    public List<LayoutScreen> screens() {
        loadedBooks.values().forEach(BookData::reset);
        loadedBooks.clear();
        runtimeResources.clear();

        List<LayoutScreen> result = new ArrayList<>();
        selectedBooks().stream()
            .sorted(Comparator.comparing(ResourceLocation::toString))
            .forEach(bookId -> addBook(result, bookId, loadBook(bookId)));
        return List.copyOf(result);
    }

    @Override
    public LayoutCapture capture(
        Minecraft minecraft,
        LayoutScreen target,
        Screen screen,
        String language
    ) {
        if (!(screen instanceof BookScreen gui)) {
            throw new IllegalStateException("Mantle did not open a BookScreen");
        }

        List<LayoutRegion> text = new ArrayList<>();
        List<LayoutRegion> pages = new ArrayList<>();
        List<LayoutRegion> scissors = new ArrayList<>();
        if (gui.getPage_() < 0) {
            captureCover(gui, target, text, pages, scissors);
        } else {
            captureSpread(gui, target, text, pages);
        }

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
            captureControls(gui),
            List.of()
        );
    }

    @Override
    public void resetAfterAudit() {
        loadedBooks.values().forEach(BookData::reset);
        loadedBooks.clear();
        indexedLanguage = null;
        translationKeysByText = Map.of();
        restoreAdvancementListener();
    }

    private Set<ResourceLocation> selectedBooks() {
        return records.stream()
            .map(TranslationAuditIndex.ScreenRecord::bookId)
            .collect(Collectors.toSet());
    }

    private BookData loadBook(ResourceLocation bookId) {
        if (!ForgeRegistries.ITEMS.containsKey(bookId)) {
            throw new IllegalStateException("Mantle book item is missing: " + bookId);
        }
        Item item = ForgeRegistries.ITEMS.getValue(bookId);
        ItemStack stack = item.getDefaultInstance();
        BookData book = bookFromItem(item, stack, bookId);
        book.reset();
        book.load();
        if (book.getPageCount(null) == 0) {
            throw new IllegalStateException("Mantle book has no pages: " + bookId);
        }
        loadedBooks.put(bookId, book);
        return book;
    }

    private void addBook(
        List<LayoutScreen> result,
        ResourceLocation bookId,
        BookData book
    ) {
        Item item = ForgeRegistries.ITEMS.getValue(bookId);
        if (item == null) {
            throw new IllegalStateException("Mantle book item is missing: " + bookId);
        }
        ItemStack stack = item.getDefaultInstance();
        String firstPage = pageLocation(book.findPage(0, null), book.sections);
        TranslationAuditIndex.ScreenRecord cover = coverRecord(bookId);
        result.add(new LayoutScreen(
            ENGINE,
            bookId.toString(),
            ENGINE + ":" + bookId + ":cover",
            cover == null ? "<runtime>" : cover.resource(),
            "<cover>",
            null,
            source(cover),
            screenFactory(stack, book, firstPage, -1)
        ));

        for (SpreadTarget target : spreadTargets(book.getPageCount(null))) {
            PageData page = book.findPage(target.firstPage(), null);
            TranslationAuditIndex.ScreenRecord indexed = pageRecord(bookId, page);
            result.add(new LayoutScreen(
                ENGINE,
                bookId.toString(),
                ENGINE + ":" + bookId + ":spread/" + target.firstPage(),
                indexed == null ? "<runtime>" : indexed.resource(),
                page == null ? "<runtime>" : page.name,
                target.firstPage(),
                source(indexed),
                screenFactory(stack, book, firstPage, target.spread())
            ));
        }
    }

    private Supplier<Screen> screenFactory(
        ItemStack stack,
        BookData book,
        String firstPage,
        int spread
    ) {
        return () -> {
            captureAdvancementListener();
            BookScreen screen = new BookScreen(
                stack.getHoverName(),
                book,
                firstPage,
                null,
                null
            );
            screen.advancementCache = null;
            disableAnimations(screen);
            screen.mouseInput = false;
            if (spread < 0) {
                screen.openCover();
            } else {
                screen._setPage(spread);
            }
            return screen;
        };
    }

    private void captureCover(
        BookScreen gui,
        LayoutScreen target,
        List<LayoutRegion> text,
        List<LayoutRegion> pages,
        List<LayoutRegion> scissors
    ) {
        double left = gui.width / 2.0 - BookScreen.PAGE_WIDTH_UNSCALED / 2.0;
        double top = gui.height / 2.0 - BookScreen.PAGE_HEIGHT_UNSCALED / 2.0;
        Font font = Minecraft.getInstance().font;
        addCoverText(
            "title",
            gui.book.appearance.title,
            2.5,
            3,
            gui.height / 2.0 - font.lineHeight / 2.0,
            -4,
            font,
            target.textSource(),
            left,
            top,
            text,
            pages,
            scissors
        );
        addCoverText(
            "subtitle",
            gui.book.appearance.subtitle,
            1.5,
            7,
            gui.height / 2.0 + 100 - font.lineHeight * 2.0,
            0,
            font,
            target.textSource(),
            left,
            top,
            text,
            pages,
            scissors
        );
    }

    private void addCoverText(
        String id,
        String value,
        double maxScale,
        double xOffset,
        double baseY,
        double scaledYOffset,
        Font font,
        String source,
        double pageLeft,
        double pageTop,
        List<LayoutRegion> text,
        List<LayoutRegion> pages,
        List<LayoutRegion> scissors
    ) {
        int width = font.width(value);
        if (value.isEmpty() || width <= 0) {
            return;
        }
        double scale = Math.min(maxScale, (double) BookScreen.PAGE_WIDTH / width);
        String page = "cover#" + id;
        LayoutRegion bounds = new LayoutRegion(
            "cover",
            LayoutRegion.Kind.PAGE,
            page,
            -1,
            pageLeft,
            pageTop,
            BookScreen.PAGE_WIDTH_UNSCALED,
            BookScreen.PAGE_HEIGHT_UNSCALED
        );
        pages.add(bounds);
        scissors.add(new LayoutRegion(
            "cover-clip",
            LayoutRegion.Kind.SCISSOR,
            page,
            -1,
            bounds.x(),
            bounds.y(),
            bounds.width(),
            bounds.height()
        ));
        text.add(new LayoutRegion(
            "cover-" + id,
            LayoutRegion.Kind.TEXT,
            page,
            0,
            pageLeft + BookScreen.PAGE_WIDTH_UNSCALED / 2.0
                + scale * (xOffset - width / 2.0),
            baseY + scale * scaledYOffset,
            width * scale,
            font.lineHeight * scale,
            source
        ));
    }

    private void captureSpread(
        BookScreen gui,
        LayoutScreen target,
        List<LayoutRegion> text,
        List<LayoutRegion> pages
    ) {
        Font font = gui.getFontRenderer();
        ResourceLocation bookId = ResourceLocation.parse(target.book());
        List<TranslationAuditIndex.ScreenRecord> bookRecords = records.stream()
            .filter(record -> record.bookId().equals(bookId))
            .toList();
        for (int side = 0; side < 2; side++) {
            int pageNumber = gui.getPage(side);
            if (pageNumber < 0) {
                continue;
            }
            PageData pageData = gui.book.findPage(pageNumber, null);
            String logicalPage = logicalPage(bookId, pageData, gui.book.sections);
            List<TranslationAuditIndex.ScreenRecord> pageRecords = pageRecords(
                bookId,
                pageData
            );
            int firstText = text.size();
            int firstPage = pages.size();
            List<BookElement> elements = gui.getElements(side);
            for (int elementIndex = 0; elementIndex < elements.size(); elementIndex++) {
                BookElement element = elements.get(elementIndex);
                if (element instanceof TextElement textElement) {
                    captureTextData(
                        gui,
                        side,
                        elementIndex,
                        textElement,
                        font,
                        pageRecords,
                        bookRecords,
                        text,
                        pages
                    );
                } else if (element instanceof TextComponentElement componentElement) {
                    captureComponentData(
                        gui,
                        side,
                        elementIndex,
                        componentElement,
                        font,
                        pageRecords,
                        bookRecords,
                        text,
                        pages
                    );
                }
            }
            tagTextLogicalPages(text, firstText, bookId, logicalPage);
            tagLogicalPage(pages, firstPage, logicalPage);
        }
    }

    private void tagTextLogicalPages(
        List<LayoutRegion> regions,
        int first,
        ResourceLocation bookId,
        String fallback
    ) {
        for (int index = first; index < regions.size(); index++) {
            LayoutRegion region = regions.get(index);
            String logicalPage = region.resource().equals("<runtime>")
                ? fallback
                : resourceLogicalPage(bookId, region.resource());
            regions.set(index, region.withLogicalPage(logicalPage));
        }
    }

    static String resourceLogicalPage(
        ResourceLocation bookId,
        String resource
    ) {
        return ENGINE + ":" + bookId + ":resource/" + resource;
    }

    private String logicalPage(
        ResourceLocation bookId,
        PageData page,
        List<SectionData> sections
    ) {
        return ENGINE + ":" + bookId + ":page/" + pageLocation(page, sections);
    }

    private void tagLogicalPage(
        List<LayoutRegion> regions,
        int first,
        String logicalPage
    ) {
        for (int index = first; index < regions.size(); index++) {
            regions.set(index, regions.get(index).withLogicalPage(logicalPage));
        }
    }

    private void captureTextData(
        BookScreen gui,
        int side,
        int elementIndex,
        TextElement element,
        Font font,
        List<TranslationAuditIndex.ScreenRecord> pageRecords,
        List<TranslationAuditIndex.ScreenRecord> bookRecords,
        List<LayoutRegion> text,
        List<LayoutRegion> pages
    ) {
        ElementGeometry geometry = addElementBounds(
            gui,
            side,
            elementIndex,
            element.x,
            element.y,
            element.width,
            element.height,
            pages
        );
        int atX = element.x;
        int atY = element.y;
        float previousScale = 1;
        int itemIndex = 0;
        for (TextData item : element.text) {
            int currentItem = itemIndex++;
            if (item == null) {
                continue;
            }
            if (item.text == null || item.text.isEmpty()) {
                if (linebreak(item, TEXT_LINEBREAK)) {
                    atX = element.x;
                    atY += font.lineHeight;
                }
                continue;
            }
            if (item.text.equals("\n")) {
                atX = element.x;
                atY += font.lineHeight;
                continue;
            }
            if (item.paragraph) {
                atX = element.x;
                atY = (int) (atY + font.lineHeight * 2 * previousScale);
            }
            previousScale = item.scale;
            String modifiers = modifiers(item);
            String rendered = TextDataRenderer.translateString(item.text);
            TextOrigin origin = textOrigin(
                item.text,
                rendered,
                pageRecords,
                bookRecords
            );
            String[] allLines = TextDataRenderer.cropStringBySize(
                rendered,
                modifiers,
                element.width,
                Short.MAX_VALUE,
                element.width - (atX - element.x),
                font,
                item.scale
            );
            String[] lines = TextDataRenderer.cropStringBySize(
                rendered,
                modifiers,
                element.width,
                element.height - (atY - element.y),
                element.width - (atX - element.x),
                font,
                item.scale
            );
            for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
                String visible = RenderedTextGeometry.trimTrailingWhitespace(
                    lines[lineIndex]
                );
                addLine(
                    geometry,
                    "text-" + elementIndex + "-" + currentItem + "-" + lineIndex,
                    elementIndex,
                    atX,
                    atY,
                    font.width(modifiers + visible) * item.scale,
                    renderedLineHeight(font, item.scale),
                    visible,
                    origin,
                    text
                );
                if (lineIndex < lines.length - 1) {
                    atY += font.lineHeight;
                    atX = element.x;
                }
            }
            if (!Arrays.equals(lines, allLines)) {
                addClippedText(
                    geometry,
                    "clipped-text-" + elementIndex + "-" + currentItem,
                    elementIndex,
                    clippedText(lines, allLines),
                    origin,
                    text
                );
            }
            atX = (int) (atX + font.width(lines[lines.length - 1]) * item.scale);
            if (linebreak(item, TEXT_LINEBREAK)
                || atX - element.x >= element.width) {
                atX = element.x;
                atY = (int) (atY + font.lineHeight * item.scale);
            }
        }
    }

    private void captureComponentData(
        BookScreen gui,
        int side,
        int elementIndex,
        TextComponentElement element,
        Font font,
        List<TranslationAuditIndex.ScreenRecord> pageRecords,
        List<TranslationAuditIndex.ScreenRecord> bookRecords,
        List<LayoutRegion> text,
        List<LayoutRegion> pages
    ) {
        ElementGeometry geometry = addElementBounds(
            gui,
            side,
            elementIndex,
            element.x,
            element.y,
            element.width,
            element.height,
            pages
        );
        int atX = element.x;
        int atY = element.y;
        float previousScale = 1;
        int itemIndex = 0;
        for (TextComponentData item : element.text) {
            int currentItem = itemIndex++;
            if (item == null) {
                continue;
            }
            if (item.text == null) {
                if (linebreak(item, COMPONENT_LINEBREAK)) {
                    atX = element.x;
                    atY += font.lineHeight;
                }
                continue;
            }
            if (item.text.getString().equals("\n")) {
                atX = element.x;
                atY += font.lineHeight;
                continue;
            }
            if (item.isParagraph) {
                atX = element.x;
                atY = (int) (atY + font.lineHeight * 2 * previousScale);
            }
            previousScale = item.scale;
            TextOrigin origin = componentOrigin(
                item.text,
                pageRecords,
                bookRecords
            );
            List<FormattedText> allLines = TextComponentDataRenderer
                .splitTextComponentBySize(
                    item.text,
                    element.width,
                    Short.MAX_VALUE,
                    element.width - (atX - element.x),
                    font,
                    item.scale
                );
            List<FormattedText> lines = TextComponentDataRenderer
                .splitTextComponentBySize(
                    item.text,
                    element.width,
                    element.height - (atY - element.y),
                    element.width - (atX - element.x),
                    font,
                    item.scale
                );
            for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
                FormattedText line = lines.get(lineIndex);
                addLine(
                    geometry,
                    "component-" + elementIndex + "-" + currentItem + "-" + lineIndex,
                    elementIndex,
                    atX,
                    atY,
                    font.width(Language.getInstance().getVisualOrder(line)) * item.scale,
                    renderedLineHeight(font, item.scale),
                    line.getString(),
                    origin,
                    text
                );
                if (lineIndex < lines.size() - 1) {
                    atY += font.lineHeight;
                    atX = element.x;
                }
            }
            if (!sameText(lines, allLines)) {
                addClippedText(
                    geometry,
                    "clipped-component-" + elementIndex + "-" + currentItem,
                    elementIndex,
                    clippedTextComponents(lines, allLines),
                    origin,
                    text
                );
            }
            FormattedText last = lines.get(lines.size() - 1);
            atX = (int) (
                atX
                    + font.width(Language.getInstance().getVisualOrder(last))
                    * item.scale
            );
            if (linebreak(item, COMPONENT_LINEBREAK)
                || atX - element.x >= element.width) {
                atX = element.x;
                atY = (int) (atY + font.lineHeight * item.scale);
            }
        }
    }

    private ElementGeometry addElementBounds(
        BookScreen gui,
        int side,
        int elementIndex,
        int x,
        int y,
        int width,
        int height,
        List<LayoutRegion> pages
    ) {
        String sideName = side == 0 ? "left" : "right";
        String page = sideName + "#text-" + elementIndex;
        double left = pageLeft(gui, side);
        double top = pageTop(gui);
        pages.add(withHorizontalRenderingSlop(new LayoutRegion(
            "page-" + sideName,
            LayoutRegion.Kind.PAGE,
            page,
            -1,
            left,
            top,
            BookScreen.PAGE_WIDTH * BookScreen.PAGE_SCALE,
            BookScreen.PAGE_HEIGHT * BookScreen.PAGE_SCALE
        )));
        return new ElementGeometry(
            page,
            left,
            top,
            left + x * BookScreen.PAGE_SCALE,
            top + (y + height) * BookScreen.PAGE_SCALE,
            width * BookScreen.PAGE_SCALE
        );
    }

    static LayoutRegion withHorizontalRenderingSlop(LayoutRegion bounds) {
        return new LayoutRegion(
            bounds.id(),
            bounds.kind(),
            bounds.page(),
            bounds.line(),
            bounds.x() - HORIZONTAL_RENDERING_SLOP,
            bounds.y(),
            bounds.width() + HORIZONTAL_RENDERING_SLOP * 2,
            bounds.height(),
            bounds.source(),
            bounds.resource(),
            bounds.content(),
            bounds.logicalPage()
        );
    }

    static double renderedLineHeight(Font font, float scale) {
        return Math.max(1, font.lineHeight - 1) * scale;
    }

    private void addClippedText(
        ElementGeometry geometry,
        String id,
        int elementIndex,
        String content,
        TextOrigin origin,
        List<LayoutRegion> text
    ) {
        text.add(new LayoutRegion(
            id,
            LayoutRegion.Kind.CLIPPED_TEXT,
            geometry.page(),
            elementIndex * 10000,
            geometry.elementLeft(),
            geometry.elementBottom(),
            geometry.elementWidth(),
            1,
            origin.source(),
            origin.resource(),
            content
        ));
    }

    private boolean sameText(
        List<FormattedText> rendered,
        List<FormattedText> complete
    ) {
        if (rendered.size() != complete.size()) {
            return false;
        }
        for (int index = 0; index < rendered.size(); index++) {
            if (!rendered.get(index).getString().equals(complete.get(index).getString())) {
                return false;
            }
        }
        return true;
    }

    private String clippedText(String[] rendered, String[] complete) {
        return clippedText(
            Arrays.asList(rendered),
            Arrays.asList(complete)
        );
    }

    private String clippedTextComponents(
        List<FormattedText> rendered,
        List<FormattedText> complete
    ) {
        return clippedText(
            rendered.stream().map(FormattedText::getString).toList(),
            complete.stream().map(FormattedText::getString).toList()
        );
    }

    private String clippedText(List<String> rendered, List<String> complete) {
        int firstDifference = 0;
        while (firstDifference < rendered.size()
            && firstDifference < complete.size()
            && rendered.get(firstDifference).equals(complete.get(firstDifference))) {
            firstDifference++;
        }
        if (firstDifference >= complete.size()) {
            return "";
        }
        return String.join(" ", complete.subList(firstDifference, complete.size()));
    }

    private void addLine(
        ElementGeometry geometry,
        String id,
        int elementIndex,
        int x,
        int y,
        double width,
        double height,
        String content,
        TextOrigin origin,
        List<LayoutRegion> text
    ) {
        if (width <= 0 || height <= 0) {
            return;
        }
        text.add(new LayoutRegion(
            id,
            LayoutRegion.Kind.TEXT,
            geometry.page(),
            elementIndex * 10000 + y,
            geometry.left() + x * BookScreen.PAGE_SCALE,
            geometry.top() + y * BookScreen.PAGE_SCALE,
            width * BookScreen.PAGE_SCALE,
            height * BookScreen.PAGE_SCALE,
            origin.source(),
            origin.resource(),
            content
        ));
    }

    private List<LayoutRegion> captureControls(BookScreen gui) {
        List<LayoutRegion> result = new ArrayList<>();
        int index = 0;
        for (GuiEventListener child : gui.children()) {
            if (!(child instanceof AbstractWidget widget) || !widget.visible) {
                continue;
            }
            result.add(new LayoutRegion(
                "control-" + index++,
                LayoutRegion.Kind.CONTROL,
                widget.getX() < gui.width / 2 ? "left" : "right",
                -1,
                widget.getX(),
                widget.getY(),
                widget.getWidth(),
                widget.getHeight()
            ));
        }
        return List.copyOf(result);
    }

    private double pageLeft(BookScreen gui, int side) {
        return side == 0
            ? gui.width / 2.0 - BookScreen.PAGE_WIDTH_UNSCALED
                + BookScreen.PAGE_PADDING_LEFT + BookScreen.PAGE_MARGIN
            : gui.width / 2.0 + BookScreen.PAGE_PADDING_RIGHT
                + BookScreen.PAGE_MARGIN;
    }

    private double pageTop(BookScreen gui) {
        return gui.height / 2.0 - BookScreen.PAGE_HEIGHT_UNSCALED / 2.0
            + BookScreen.PAGE_PADDING_TOP + BookScreen.PAGE_MARGIN;
    }

    private String modifiers(TextData item) {
        StringBuilder result = new StringBuilder();
        if (item.useOldColor) {
            ChatFormatting color = ChatFormatting.getByName(item.color);
            if (color != null) {
                result.append(color);
            }
        }
        if (item.bold) {
            result.append(ChatFormatting.BOLD);
        }
        if (item.italic) {
            result.append(ChatFormatting.ITALIC);
        }
        if (item.underlined) {
            result.append(ChatFormatting.UNDERLINE);
        }
        if (item.strikethrough) {
            result.append(ChatFormatting.STRIKETHROUGH);
        }
        if (item.obfuscated) {
            result.append(ChatFormatting.OBFUSCATED);
        }
        return result.toString();
    }

    private BookData bookFromItem(
        Item item,
        ItemStack stack,
        ResourceLocation bookId
    ) {
        try {
            Method getBook = item.getClass().getMethod("getBook", ItemStack.class);
            Object result = getBook.invoke(item, stack);
            if (result instanceof BookData book) {
                return book;
            }
        } catch (NoSuchMethodException ignored) {
            // Mantle before 1.11.104 did not expose a common book-item API.
        } catch (IllegalAccessException | InvocationTargetException exception) {
            throw new IllegalStateException(
                "Mantle book item failed to provide BookData: " + bookId,
                exception
            );
        }

        if (bookId.getNamespace().equals("tconstruct")) {
            return legacyTinkerBook(item, bookId);
        }
        throw new IllegalStateException(
            "Mantle book item has no supported BookData provider: " + bookId
        );
    }

    private BookData legacyTinkerBook(Item item, ResourceLocation bookId) {
        try {
            Field bookTypeField = item.getClass().getDeclaredField("bookType");
            bookTypeField.setAccessible(true);
            Object bookType = bookTypeField.get(item);
            Class<?> tinkerBook = Class.forName(
                "slimeknights.tconstruct.library.client.book.TinkerBook"
            );
            Method getBook = tinkerBook.getMethod("getBook", bookType.getClass());
            Object result = getBook.invoke(null, bookType);
            if (result instanceof BookData book) {
                return book;
            }
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(
                "Legacy Tinkers' Construct book failed to provide BookData: "
                    + bookId,
                exception
            );
        }
        throw new IllegalStateException(
            "Legacy Tinkers' Construct book did not provide BookData: " + bookId
        );
    }

    private static void disableAnimations(BookScreen screen) {
        Field field = publicField(BookScreen.class, "enableAnimations");
        if (field == null) {
            return;
        }
        try {
            field.setBoolean(screen, false);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException(
                "Mantle book animations could not be disabled",
                exception
            );
        }
    }

    private static boolean linebreak(Object item, Field field) {
        if (field == null) {
            return false;
        }
        try {
            return field.getBoolean(item);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException(
                "Mantle text linebreak could not be read",
                exception
            );
        }
    }

    private static Field publicField(Class<?> type, String name) {
        try {
            return type.getField(name);
        } catch (NoSuchFieldException ignored) {
            return null;
        }
    }

    private TranslationAuditIndex.ScreenRecord coverRecord(ResourceLocation bookId) {
        return records.stream()
            .filter(record -> record.bookId().equals(bookId))
            .filter(record -> record.resource().endsWith("/language.lang"))
            .findFirst()
            .orElse(null);
    }

    private TranslationAuditIndex.ScreenRecord pageRecord(
        ResourceLocation bookId,
        PageData page
    ) {
        return pageRecords(bookId, page).stream().findFirst().orElse(null);
    }

    private List<TranslationAuditIndex.ScreenRecord> pageRecords(
        ResourceLocation bookId,
        PageData page
    ) {
        return matchingPageRecords(records, bookId, page);
    }

    static List<TranslationAuditIndex.ScreenRecord> matchingPageRecords(
        List<TranslationAuditIndex.ScreenRecord> records,
        ResourceLocation bookId,
        PageData page
    ) {
        if (page == null) {
            return List.of();
        }
        boolean hasData = page.data != null && !page.data.equals("no-load");
        return records.stream()
            .filter(record -> record.bookId().equals(bookId))
            .filter(record -> (hasData
                && record.resource().endsWith("/" + page.data))
                || matchesPageName(record.entry(), page.name))
            .toList();
    }

    static boolean matchesPageName(String recordEntry, String pageName) {
        if (recordEntry.equals(pageName)) {
            return true;
        }
        String normalized = pageName.replace('.', '_');
        return normalized.equals(recordEntry)
            || normalized.endsWith("_" + recordEntry);
    }

    private String source(TranslationAuditIndex.ScreenRecord record) {
        return record == null
            ? "<runtime>"
            : record.textSourceType() + ":" + record.textSource();
    }

    private TextOrigin textOrigin(
        String raw,
        String rendered,
        List<TranslationAuditIndex.ScreenRecord> pageRecords,
        List<TranslationAuditIndex.ScreenRecord> bookRecords
    ) {
        String translationKey = translationKeySource(raw);
        if (translationKey != null) {
            return new TextOrigin("<runtime>", translationKey);
        }
        TextOrigin pageOrigin = uniqueOrigin(pageRecords.stream()
            .map(TranslationAuditIndex.ScreenRecord::resource)
            .distinct()
            .map(resource -> new TextOrigin(
                resource,
                textSource(resource, raw, rendered)
            ))
            .filter(origin -> origin.source() != null)
            .toList());
        if (!pageOrigin.equals(TextOrigin.RUNTIME)) {
            return pageOrigin;
        }
        TextOrigin bookOrigin = uniqueOrigin(bookRecords.stream()
            .map(TranslationAuditIndex.ScreenRecord::resource)
            .distinct()
            .map(resource -> new TextOrigin(
                resource,
                translations.source(resource, raw, rendered)
            ))
            .filter(origin -> origin.source() != null)
            .toList());
        return bookOrigin.equals(TextOrigin.RUNTIME)
            ? runtimeTranslationOrigin(rendered)
            : bookOrigin;
    }

    private TextOrigin uniqueOrigin(List<TextOrigin> matches) {
        return matches.size() == 1 ? matches.get(0) : TextOrigin.RUNTIME;
    }

    private TextOrigin componentOrigin(
        Component component,
        List<TranslationAuditIndex.ScreenRecord> pageRecords,
        List<TranslationAuditIndex.ScreenRecord> bookRecords
    ) {
        if (component.getContents() instanceof TranslatableContents translatable) {
            return new TextOrigin(
                "<runtime>",
                "translation_key:" + translatable.getKey()
            );
        }
        for (Component sibling : component.getSiblings()) {
            TextOrigin origin = componentOrigin(
                sibling,
                pageRecords,
                bookRecords
            );
            if (!origin.equals(TextOrigin.RUNTIME)) {
                return origin;
            }
        }
        return textOrigin(
            component.getString(),
            component.getString(),
            pageRecords,
            bookRecords
        );
    }

    static String translationKeySource(String raw) {
        Matcher matcher = TRANSLATION_KEY.matcher(raw);
        if (!matcher.find()) {
            return null;
        }
        String key = matcher.group(1);
        return matcher.find() ? null : "translation_key:" + key;
    }

    private TextOrigin runtimeTranslationOrigin(String rendered) {
        Language language = Language.getInstance();
        if (language != indexedLanguage) {
            Map<String, String> localized = new LinkedHashMap<>();
            for (String key : translationKeys) {
                if (language.has(key)) {
                    localized.put(key, language.getOrDefault(key));
                }
            }
            translationKeysByText = uniqueTranslationKeys(localized);
            indexedLanguage = language;
        }
        String key = translationKeysByText.get(rendered);
        return key == null
            ? TextOrigin.RUNTIME
            : new TextOrigin("<runtime>", "translation_key:" + key);
    }

    private String textSource(String resource, String raw, String rendered) {
        String source = translations.source(resource, raw, rendered);
        if (source != null) {
            return source;
        }
        return BookLayoutTranslationIndex.source(
            runtimeResources
                .computeIfAbsent(resource, this::loadRuntimeResource)
                .orElse(null),
            raw,
            rendered
        );
    }

    private Optional<JsonElement> loadRuntimeResource(String resource) {
        if (!resource.startsWith("assets/")) {
            return Optional.empty();
        }
        String path = resource.substring("assets/".length());
        int separator = path.indexOf('/');
        if (separator <= 0 || separator == path.length() - 1) {
            return Optional.empty();
        }
        ResourceLocation location = ResourceLocation.parse(
            path.substring(0, separator) + ":" + path.substring(separator + 1)
        );
        Optional<Resource> selected = Minecraft.getInstance()
            .getResourceManager()
            .getResource(location);
        if (selected.isEmpty()) {
            return Optional.empty();
        }
        try (Reader reader = selected.get().openAsReader()) {
            return Optional.of(JsonParser.parseReader(reader));
        } catch (IOException | RuntimeException ignored) {
            return Optional.empty();
        }
    }

    static Map<String, String> uniqueTranslationKeys(
        Map<String, String> translations
    ) {
        Map<String, String> result = new LinkedHashMap<>();
        Set<String> ambiguous = new HashSet<>();
        translations.forEach((key, value) -> {
            if (value == null || value.isEmpty() || ambiguous.contains(value)) {
                return;
            }
            String previous = result.putIfAbsent(value, key);
            if (previous != null && !previous.equals(key)) {
                result.remove(value);
                ambiguous.add(value);
            }
        });
        return Map.copyOf(result);
    }

    static String pageLocation(PageData page, List<SectionData> sections) {
        if (page == null) {
            throw new IllegalStateException("Mantle book has no first page location");
        }
        SectionData section = page.parent;
        if (section == null) {
            section = sections.stream()
                .filter(candidate -> candidate.pages != null)
                .filter(candidate -> candidate.pages.contains(page))
                .findFirst()
                .orElse(null);
        }
        if (section == null || section.name == null || page.name == null) {
            throw new IllegalStateException("Mantle book has no first page location");
        }
        return section.name + "." + page.name;
    }

    private void captureAdvancementListener() {
        if (advancementListenerCaptured) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        ClientAdvancements advancements = minecraft.player.connection.getAdvancements();
        previousAdvancementListener = ObfuscationReflectionHelper.getPrivateValue(
            ClientAdvancements.class,
            advancements,
            ADVANCEMENT_LISTENER_FIELD
        );
        advancementListenerCaptured = true;
    }

    private void restoreAdvancementListener() {
        if (!advancementListenerCaptured) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.connection.getAdvancements().setListener(
                previousAdvancementListener
            );
        }
        previousAdvancementListener = null;
        advancementListenerCaptured = false;
    }

    static List<SpreadTarget> spreadTargets(int pageCount) {
        if (pageCount <= 0) {
            return List.of();
        }
        List<SpreadTarget> result = new ArrayList<>();
        result.add(new SpreadTarget(0, 0));
        int spread = 1;
        for (int firstPage = 1; firstPage < pageCount; firstPage += 2) {
            result.add(new SpreadTarget(spread++, firstPage));
        }
        return List.copyOf(result);
    }

    record SpreadTarget(int spread, int firstPage) {
    }

    private record TextOrigin(String resource, String source) {
        private static final TextOrigin RUNTIME = new TextOrigin(
            "<runtime>",
            "<runtime>"
        );
    }

    private record ElementGeometry(
        String page,
        double left,
        double top,
        double elementLeft,
        double elementBottom,
        double elementWidth
    ) {
    }
}
