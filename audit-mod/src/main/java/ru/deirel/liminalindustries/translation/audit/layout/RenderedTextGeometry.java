package ru.deirel.liminalindustries.translation.audit.layout;

final class RenderedTextGeometry {
    private RenderedTextGeometry() {
    }

    static String trimTrailingWhitespace(String text) {
        int end = text.length();
        while (end > 0) {
            char character = text.charAt(end - 1);
            if (!Character.isWhitespace(character) && !Character.isSpaceChar(character)) {
                break;
            }
            end--;
        }
        return text.substring(0, end);
    }

    static LayoutRegion region(
        String id,
        String page,
        int line,
        double bookLeft,
        double bookTop,
        double ownerLeft,
        double ownerTop,
        double anchorX,
        double anchorY,
        double wordX,
        double wordY,
        double renderedWidth,
        double renderedHeight,
        double rendererScale,
        double screenScale,
        String source
    ) {
        double transformedX = anchorX + (wordX - anchorX) * rendererScale;
        double transformedY = anchorY + (wordY - anchorY) * rendererScale;
        return new LayoutRegion(
            id,
            LayoutRegion.Kind.TEXT,
            page,
            line,
            (bookLeft + ownerLeft + transformedX) * screenScale,
            (bookTop + ownerTop + transformedY) * screenScale,
            renderedWidth * rendererScale * screenScale,
            renderedHeight * rendererScale * screenScale,
            source
        );
    }
}
