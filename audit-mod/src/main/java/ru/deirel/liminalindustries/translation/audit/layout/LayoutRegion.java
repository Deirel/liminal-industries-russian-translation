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
    String source
) {
    private static final double GEOMETRY_EPSILON = 0.0001;

    public enum Kind {
        TEXT,
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
        this(id, kind, page, line, x, y, width, height, null);
    }

    public double right() {
        return x + width;
    }

    public double bottom() {
        return y + height;
    }

    public boolean intersects(LayoutRegion other) {
        return x < other.right() - GEOMETRY_EPSILON
            && right() > other.x() + GEOMETRY_EPSILON
            && y < other.bottom() - GEOMETRY_EPSILON
            && bottom() > other.y() + GEOMETRY_EPSILON;
    }

    public boolean contains(LayoutRegion other) {
        return x <= other.x() + GEOMETRY_EPSILON
            && y <= other.y() + GEOMETRY_EPSILON
            && right() + GEOMETRY_EPSILON >= other.right()
            && bottom() + GEOMETRY_EPSILON >= other.bottom();
    }
}
