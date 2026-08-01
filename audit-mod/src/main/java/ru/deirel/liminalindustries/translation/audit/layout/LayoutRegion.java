package ru.deirel.liminalindustries.translation.audit.layout;

public record LayoutRegion(
    String id,
    Kind kind,
    String page,
    int line,
    double x,
    double y,
    double width,
    double height,
    String source,
    String resource,
    String content,
    String logicalPage
) {
    private static final double FLOATING_POINT_EPSILON = 0.0001;

    public enum Kind {
        TEXT,
        CLIPPED_TEXT,
        PAGE,
        SCISSOR,
        CONTROL
    }

    public LayoutRegion(
        String id,
        Kind kind,
        String page,
        int line,
        double x,
        double y,
        double width,
        double height
    ) {
        this(id, kind, page, line, x, y, width, height, null, null, null, null);
    }

    public LayoutRegion(
        String id,
        Kind kind,
        String page,
        int line,
        double x,
        double y,
        double width,
        double height,
        String source
    ) {
        this(id, kind, page, line, x, y, width, height, source, null, null, null);
    }

    public LayoutRegion(
        String id,
        Kind kind,
        String page,
        int line,
        double x,
        double y,
        double width,
        double height,
        String source,
        String resource
    ) {
        this(
            id,
            kind,
            page,
            line,
            x,
            y,
            width,
            height,
            source,
            resource,
            null,
            null
        );
    }

    public LayoutRegion(
        String id,
        Kind kind,
        String page,
        int line,
        double x,
        double y,
        double width,
        double height,
        String source,
        String resource,
        String content
    ) {
        this(
            id,
            kind,
            page,
            line,
            x,
            y,
            width,
            height,
            source,
            resource,
            content,
            null
        );
    }

    public LayoutRegion withLogicalPage(String value) {
        return new LayoutRegion(
            id,
            kind,
            page,
            line,
            x,
            y,
            width,
            height,
            source,
            resource,
            content,
            value
        );
    }

    public double right() {
        return x + width;
    }

    public double bottom() {
        return y + height;
    }

    public boolean intersects(LayoutRegion other) {
        return intersects(other, 0);
    }

    public boolean intersects(LayoutRegion other, double tolerance) {
        double effectiveTolerance = effectiveTolerance(tolerance);
        return x < other.right() - effectiveTolerance
            && right() > other.x() + effectiveTolerance
            && y < other.bottom() - effectiveTolerance
            && bottom() > other.y() + effectiveTolerance;
    }

    public boolean contains(LayoutRegion other) {
        return contains(other, 0);
    }

    public boolean contains(LayoutRegion other, double tolerance) {
        double effectiveTolerance = effectiveTolerance(tolerance);
        return x <= other.x() + effectiveTolerance
            && y <= other.y() + effectiveTolerance
            && right() + effectiveTolerance >= other.right()
            && bottom() + effectiveTolerance >= other.bottom();
    }

    private static double effectiveTolerance(double tolerance) {
        if (!Double.isFinite(tolerance) || tolerance < 0) {
            throw new IllegalArgumentException(
                "Geometry tolerance must be a finite non-negative number"
            );
        }
        return Math.max(FLOATING_POINT_EPSILON, tolerance);
    }
}
