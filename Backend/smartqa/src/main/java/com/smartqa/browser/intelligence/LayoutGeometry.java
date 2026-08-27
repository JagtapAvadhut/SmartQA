package com.smartqa.browser.intelligence;

/**
 * Bounding-box evidence helpers. Coordinates never become locators.
 */
public final class LayoutGeometry {

    public record Box(double x, double y, double w, double h) {
        public double area() {
            return Math.max(0, w) * Math.max(0, h);
        }

        public double centerX() {
            return x + w / 2;
        }

        public double centerY() {
            return y + h / 2;
        }
    }

    private LayoutGeometry() {
    }

    public static Box parse(String boundingBox) {
        if (boundingBox == null || boundingBox.isBlank()) {
            return null;
        }
        String[] parts = boundingBox.split(",");
        if (parts.length < 4) {
            return null;
        }
        try {
            return new Box(
                    Double.parseDouble(parts[0].trim()),
                    Double.parseDouble(parts[1].trim()),
                    Double.parseDouble(parts[2].trim()),
                    Double.parseDouble(parts[3].trim()));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public static boolean contains(Box outer, Box inner) {
        if (outer == null || inner == null) {
            return false;
        }
        return inner.x() >= outer.x() - 1
                && inner.y() >= outer.y() - 1
                && inner.x() + inner.w() <= outer.x() + outer.w() + 1
                && inner.y() + inner.h() <= outer.y() + outer.h() + 1;
    }

    public static boolean overlaps(Box a, Box b) {
        if (a == null || b == null) {
            return false;
        }
        return a.x() < b.x() + b.w()
                && a.x() + a.w() > b.x()
                && a.y() < b.y() + b.h()
                && a.y() + a.h() > b.y();
    }

    public static double distance(Box a, Box b) {
        if (a == null || b == null) {
            return Double.MAX_VALUE;
        }
        double dx = a.centerX() - b.centerX();
        double dy = a.centerY() - b.centerY();
        return Math.sqrt(dx * dx + dy * dy);
    }

    public static boolean near(Box a, Box b, double threshold) {
        return distance(a, b) <= threshold;
    }
}
