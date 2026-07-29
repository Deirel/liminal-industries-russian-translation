package ru.deirel.liminalindustries.translation.audit.layout;

public record LayoutRegion(
    String id,
    Kind kind,
    String page,
    int line,
    double x,
    double y,
    double width,
    double height
) {
    public enum Kind {
        TEXT,
        PAGE,
        SCISSOR,
        CONTROL
    }

    public double right() {
        return x + width;
    }

    public double bottom() {
        return y + height;
    }

    public boolean intersects(LayoutRegion other) {
        return x < other.right()
            && right() > other.x()
            && y < other.bottom()
            && bottom() > other.y();
    }

    public boolean contains(LayoutRegion other) {
        return x <= other.x()
            && y <= other.y()
            && right() >= other.right()
            && bottom() >= other.bottom();
    }
}
