package ru.deirel.liminalindustries.translation.audit.layout;

import java.util.List;

public record LayoutCapture(
    String engine,
    String book,
    String screenId,
    String resource,
    String entry,
    Integer page,
    String textSource,
    String language,
    int screenWidth,
    int screenHeight,
    double guiScale,
    List<LayoutRegion> text,
    List<LayoutRegion> pages,
    List<LayoutRegion> scissors,
    List<LayoutRegion> controls,
    List<LayoutRegion> missingContent
) {
    public LayoutCapture {
        text = List.copyOf(text);
        pages = List.copyOf(pages);
        scissors = List.copyOf(scissors);
        controls = List.copyOf(controls);
        missingContent = List.copyOf(missingContent);
    }

    public LayoutCapture(
        String engine,
        String book,
        String screenId,
        String resource,
        String entry,
        Integer page,
        String textSource,
        String language,
        int screenWidth,
        int screenHeight,
        double guiScale,
        List<LayoutRegion> text,
        List<LayoutRegion> pages,
        List<LayoutRegion> scissors,
        List<LayoutRegion> controls
    ) {
        this(
            engine,
            book,
            screenId,
            resource,
            entry,
            page,
            textSource,
            language,
            screenWidth,
            screenHeight,
            guiScale,
            text,
            pages,
            scissors,
            controls,
            List.of()
        );
    }
}
