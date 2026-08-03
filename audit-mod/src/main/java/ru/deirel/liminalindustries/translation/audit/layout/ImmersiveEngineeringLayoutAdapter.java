package ru.deirel.liminalindustries.translation.audit.layout;

import blusunrize.immersiveengineering.api.ManualHelper;
import blusunrize.lib.manual.ManualEntry;
import blusunrize.lib.manual.ManualInstance;
import blusunrize.lib.manual.ManualUtils;
import blusunrize.lib.manual.SpecialManualElement;
import blusunrize.lib.manual.Tree;
import blusunrize.lib.manual.gui.GuiButtonManualLink;
import blusunrize.lib.manual.gui.GuiButtonManualNavigation;
import blusunrize.lib.manual.gui.ManualScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import ru.deirel.liminalindustries.translation.audit.TranslationAuditIndex;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class ImmersiveEngineeringLayoutAdapter implements LayoutEngineAdapter {
    private static final String ENGINE = "immersive_engineering";
    private static final String BOOK = "immersiveengineering:manual";
    private static final int PAGE_LEFT = 32;
    private static final int PAGE_TOP = 28;
    private static final int PAGE_WIDTH = 120;
    private static final int PAGE_HEIGHT = 148;
    private static final int MANUAL_WIDTH = 186;

    private final List<TranslationAuditIndex.ScreenRecord> records =
        TranslationAuditIndex.screenRecords(ENGINE);

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
        ManualInstance manual = manual();
        manual.reload();
        List<LayoutScreen> result = new ArrayList<>();
        manual.getAllEntriesAndCategories()
            .sorted(Comparator.comparing(this::nodeId))
            .forEach(node -> addNode(result, manual, node));
        return List.copyOf(result);
    }

    @Override
    public LayoutCapture capture(
        Minecraft minecraft,
        LayoutScreen target,
        Screen screen,
        String language
    ) {
        if (!(screen instanceof ManualScreen gui)) {
            throw new IllegalStateException(
                "Immersive Engineering did not open a ManualScreen"
            );
        }
        double scale = field(gui, "scaleFactor", Float.class);
        String resource = languageResource(target.resource(), language);
        List<LayoutRegion> pages = new ArrayList<>();
        List<LayoutRegion> text = new ArrayList<>();
        if (gui.currentNode.isLeaf()) {
            captureEntry(gui, target, resource, scale, pages, text);
        } else {
            captureIndex(gui, target, resource, scale, pages, text);
        }
        return new LayoutCapture(
            ENGINE,
            target.book(),
            target.id(),
            resource,
            target.entry(),
            target.page(),
            target.textSource(),
            language,
            minecraft.getWindow().getGuiScaledWidth(),
            minecraft.getWindow().getGuiScaledHeight(),
            minecraft.getWindow().getGuiScale(),
            text,
            pages,
            List.of(),
            captureControls(gui, scale),
            List.of()
        );
    }

    @Override
    public void resetAfterAudit() {
        manual().reset();
    }

    private void addNode(
        List<LayoutScreen> result,
        ManualInstance manual,
        Tree.AbstractNode<ResourceLocation, ManualEntry> node
    ) {
        if (!node.isLeaf()) {
            String id = nodeId(node);
            result.add(new LayoutScreen(
                ENGINE,
                BOOK,
                ENGINE + ":" + BOOK + ":index/" + id,
                "<runtime>",
                id,
                null,
                categorySource(node),
                screenFactory(manual, node, 0)
            ));
            return;
        }
        ManualEntry entry = node.getLeafData();
        List<TranslationAuditIndex.ScreenRecord> indexed = entryRecords(entry);
        String resource = indexed.isEmpty()
            ? runtimeResource(entry)
            : indexed.get(0).resource();
        String source = indexed.isEmpty()
            ? "resource:" + runtimeResource(entry)
            : indexed.get(0).textSourceType() + ":"
                + indexed.get(0).textSource();
        for (int page = 0; page < entry.getPageCount(); page++) {
            result.add(new LayoutScreen(
                ENGINE,
                BOOK,
                ENGINE + ":" + BOOK + ":entry/"
                    + entry.getLocation() + "/" + page,
                resource,
                entry.getLocation().toString(),
                page,
                source,
                screenFactory(manual, node, page)
            ));
        }
    }

    private java.util.function.Supplier<Screen> screenFactory(
        ManualInstance manual,
        Tree.AbstractNode<ResourceLocation, ManualEntry> node,
        int page
    ) {
        return () -> {
            ManualScreen screen = manual.getGui(false);
            if (screen == null) {
                throw new IllegalStateException("Immersive Engineering manual failed to load");
            }
            screen.setCurrentNode(node);
            screen.page = page;
            return screen;
        };
    }

    private void captureEntry(
        ManualScreen gui,
        LayoutScreen target,
        String resource,
        double scale,
        List<LayoutRegion> pages,
        List<LayoutRegion> text
    ) {
        double left = field(gui, "guiLeft", Integer.class);
        double top = field(gui, "guiTop", Integer.class);
        String logicalPage = target.entry();
        pages.add(region(
            "header-page",
            LayoutRegion.Kind.PAGE,
            "page#header",
            -1,
            left + PAGE_LEFT,
            top + 7,
            PAGE_WIDTH,
            19,
            scale
        ).withLogicalPage(logicalPage));
        pages.add(region(
            "body-page",
            LayoutRegion.Kind.PAGE,
            "page#body",
            -1,
            left + PAGE_LEFT,
            top + PAGE_TOP,
            PAGE_WIDTH,
            PAGE_HEIGHT,
            scale
        ).withLogicalPage(logicalPage));

        ManualEntry entry = gui.getCurrentPage();
        Font font = gui.getManual().fontRenderer();
        addCenteredText(
            "title",
            ChatFormatting.BOLD + entry.getTitle(),
            "page#header",
            top + 14,
            left,
            font,
            target,
            logicalPage,
            scale,
            text
        );
        addCenteredText(
            "subtitle",
            gui.getManual().formatEntrySubtext(entry.getSubtext()),
            "page#header",
            top + 22,
            left,
            font,
            target,
            logicalPage,
            scale,
            text
        );

        Object page = field(entry, "pages", List.class).get(gui.page);
        @SuppressWarnings("unchecked")
        List<String> lines = field(page, "renderText", List.class);
        SpecialManualElement special = field(
            page,
            "special",
            SpecialManualElement.class
        );
        int textOffset = special.isAbove() ? special.getPixelsTaken() : 0;
        for (int line = 0; line < lines.size(); line++) {
            String content = lines.get(line);
            int width = font.width(content);
            if (width <= 0) {
                continue;
            }
            text.add(region(
                "body-line-" + line,
                LayoutRegion.Kind.TEXT,
                "page#body",
                line,
                left + PAGE_LEFT,
                top + PAGE_TOP + textOffset + line * font.lineHeight,
                width,
                font.lineHeight,
                scale,
                target.textSource(),
                resource,
                content
            ).withLogicalPage(logicalPage));
        }
    }

    private void captureIndex(
        ManualScreen gui,
        LayoutScreen target,
        String resource,
        double scale,
        List<LayoutRegion> pages,
        List<LayoutRegion> text
    ) {
        double left = field(gui, "guiLeft", Integer.class);
        double top = field(gui, "guiTop", Integer.class);
        String logicalPage = target.entry();
        AbstractWidget list = gui.children().stream()
            .filter(AbstractWidget.class::isInstance)
            .map(AbstractWidget.class::cast)
            .filter(widget -> widget.getClass().getSimpleName().equals("ClickableList"))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("IE manual index is missing"));
        pages.add(region(
            "index-header-page",
            LayoutRegion.Kind.PAGE,
            "index#header",
            -1,
            left,
            top + 7,
            MANUAL_WIDTH,
            20,
            scale
        ).withLogicalPage(logicalPage));
        pages.add(region(
            "index-list-page",
            LayoutRegion.Kind.PAGE,
            "index#list",
            -1,
            list.getX(),
            list.getY(),
            left + MANUAL_WIDTH - list.getX(),
            list.getHeight(),
            scale
        ).withLogicalPage(logicalPage));
        Font font = gui.getManual().fontRenderer();
        String title = ChatFormatting.BOLD + ManualUtils.getTitleForNode(
            gui.currentNode,
            gui.getManual()
        );
        addCenteredText(
            "index-title",
            title,
            "index#header",
            top + 12,
            left,
            font,
            target,
            logicalPage,
            scale,
            text
        );

        String[] headers = field(list, "headers", String[].class);
        boolean[] categories = field(list, "isCategory", boolean[].class);
        int offset = field(list, "offset", Integer.class);
        int perPage = field(list, "perPage", Integer.class);
        double textScale = field(list, "textScale", Float.class);
        int lineHeight = (int) (font.lineHeight * textScale);
        for (int line = 0; line < Math.min(perPage, headers.length - offset); line++) {
            int index = offset + line;
            String content = headers[index];
            double x = list.getX() + (categories[index] ? 7 : 0);
            text.add(region(
                "index-line-" + index,
                LayoutRegion.Kind.TEXT,
                "index#list",
                line,
                x,
                list.getY() + line * lineHeight,
                font.width(content) * textScale,
                font.lineHeight * textScale,
                scale,
                target.textSource(),
                resource,
                content
            ).withLogicalPage(logicalPage));
        }
    }

    private void addCenteredText(
        String id,
        String content,
        String page,
        double y,
        double left,
        Font font,
        LayoutScreen target,
        String logicalPage,
        double scale,
        List<LayoutRegion> text
    ) {
        int width = font.width(content);
        if (width <= 0) {
            return;
        }
        text.add(region(
            id,
            LayoutRegion.Kind.TEXT,
            page,
            (int) y,
            left + (MANUAL_WIDTH - width) / 2.0,
            y - font.lineHeight / 2.0,
            width,
            font.lineHeight,
            scale,
            target.textSource(),
            target.resource(),
            content
        ).withLogicalPage(logicalPage));
    }

    private List<LayoutRegion> captureControls(ManualScreen gui, double scale) {
        List<LayoutRegion> result = new ArrayList<>();
        int index = 0;
        for (GuiEventListener child : gui.children()) {
            if (!(child instanceof AbstractWidget widget)
                || !widget.visible
                || widget instanceof GuiButtonManualLink
                || (!gui.currentNode.isLeaf()
                    && widget instanceof GuiButtonManualNavigation)
                || widget.getClass().getSimpleName().equals("ClickableList")) {
                continue;
            }
            result.add(region(
                "control-" + index++,
                LayoutRegion.Kind.CONTROL,
                gui.currentNode.isLeaf() ? "page" : "index",
                -1,
                widget.getX(),
                widget.getY(),
                widget.getWidth(),
                widget.getHeight(),
                scale
            ));
        }
        return List.copyOf(result);
    }

    private List<TranslationAuditIndex.ScreenRecord> entryRecords(
        ManualEntry entry
    ) {
        String path = entry.getLocation().getPath();
        return records.stream()
            .filter(record -> record.entry().equals(path))
            .sorted(Comparator.comparingInt(record ->
                record.page() == null ? -1 : record.page()
            ))
            .toList();
    }

    private String categorySource(
        Tree.AbstractNode<ResourceLocation, ManualEntry> node
    ) {
        return node.getNodeData() == null
            ? "translation_key:item.immersiveengineering.manual"
            : "translation_key:manual."
                + node.getNodeData().toString().replace(':', '.');
    }

    private String runtimeResource(ManualEntry entry) {
        return "assets/" + entry.getLocation().getNamespace()
            + "/manual/<language>/" + entry.getLocation().getPath() + ".txt";
    }

    private String languageResource(String resource, String language) {
        return resource
            .replace("/<language>/", "/" + language + "/")
            .replace("/ru_ru/", "/" + language + "/");
    }

    private String nodeId(Tree.AbstractNode<ResourceLocation, ManualEntry> node) {
        return node.isLeaf()
            ? "entry/" + node.getLeafData().getLocation()
            : node.getNodeData().toString();
    }

    private ManualInstance manual() {
        ManualInstance manual = ManualHelper.getManual();
        if (manual == null) {
            throw new IllegalStateException("Immersive Engineering manual is unavailable");
        }
        return manual;
    }

    private static LayoutRegion region(
        String id,
        LayoutRegion.Kind kind,
        String page,
        int line,
        double x,
        double y,
        double width,
        double height,
        double scale
    ) {
        return region(
            id,
            kind,
            page,
            line,
            x,
            y,
            width,
            height,
            scale,
            null,
            null,
            null
        );
    }

    private static LayoutRegion region(
        String id,
        LayoutRegion.Kind kind,
        String page,
        int line,
        double x,
        double y,
        double width,
        double height,
        double scale,
        String source,
        String resource,
        String content
    ) {
        return new LayoutRegion(
            id,
            kind,
            page,
            line,
            x * scale,
            y * scale,
            width * scale,
            height * scale,
            source,
            resource,
            content
        );
    }

    private static <T> T field(Object owner, String name, Class<T> type) {
        for (Class<?> cursor = owner.getClass(); cursor != null;
            cursor = cursor.getSuperclass()) {
            try {
                Field field = cursor.getDeclaredField(name);
                field.setAccessible(true);
                return type.cast(field.get(owner));
            } catch (NoSuchFieldException ignored) {
                // Continue through the hierarchy.
            } catch (ReflectiveOperationException | ClassCastException exception) {
                throw new IllegalStateException(
                    "Could not inspect Immersive Engineering layout field "
                        + cursor.getName() + "." + name,
                    exception
                );
            }
        }
        throw new IllegalStateException(
            "Missing Immersive Engineering layout field "
                + owner.getClass().getName() + "." + name
        );
    }
}
