package com.smartqa.intent;

import java.util.Locale;
import java.util.Set;

/**
 * Search hint only — never converted to absolute click coordinates.
 */
public final class LocationHint {

    public static final String AUTO = "AUTO";
    public static final String TOP_LEFT = "TOP_LEFT";
    public static final String TOP_CENTER = "TOP_CENTER";
    public static final String TOP_RIGHT = "TOP_RIGHT";
    public static final String MIDDLE_LEFT = "MIDDLE_LEFT";
    public static final String CENTER = "CENTER";
    public static final String MIDDLE_RIGHT = "MIDDLE_RIGHT";
    public static final String BOTTOM_LEFT = "BOTTOM_LEFT";
    public static final String BOTTOM_CENTER = "BOTTOM_CENTER";
    public static final String BOTTOM_RIGHT = "BOTTOM_RIGHT";
    public static final String HEADER = "HEADER";
    public static final String SIDEBAR_LEFT = "SIDEBAR_LEFT";
    public static final String SIDEBAR_RIGHT = "SIDEBAR_RIGHT";
    public static final String CONTENT = "CONTENT";
    public static final String FOOTER = "FOOTER";
    public static final String MODAL = "MODAL";
    public static final String DIALOG = "DIALOG";

    public static final Set<String> ALL = Set.of(
            AUTO, TOP_LEFT, TOP_CENTER, TOP_RIGHT,
            MIDDLE_LEFT, CENTER, MIDDLE_RIGHT,
            BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT,
            HEADER, SIDEBAR_LEFT, SIDEBAR_RIGHT, CONTENT, FOOTER, MODAL, DIALOG
    );

    private LocationHint() {
    }

    public static String normalize(String raw) {
        if (raw == null || raw.isBlank() || isNullToken(raw)) {
            return AUTO;
        }
        String value = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return ALL.contains(value) ? value : AUTO;
    }

    public static boolean isAuto(String location) {
        return location == null || location.isBlank() || AUTO.equalsIgnoreCase(location);
    }

    /**
     * Score 0..1 how well a bounding box matches a location hint.
     * Uses relative position within an estimated viewport — never absolute screen coords for clicking.
     */
    public static double score(String location, String boundingBox, boolean inHeaderRegion) {
        String hint = normalize(location);
        if (isAuto(hint)) {
            return 0.5;
        }
        if (HEADER.equals(hint)) {
            return inHeaderRegion ? 1.0 : 0.2;
        }
        if (FOOTER.equals(hint)) {
            return inHeaderRegion ? 0.1 : footerBias(boundingBox);
        }
        if (MODAL.equals(hint) || DIALOG.equals(hint)) {
            return centerBias(boundingBox);
        }
        if (SIDEBAR_LEFT.equals(hint)) {
            return leftBias(boundingBox);
        }
        if (SIDEBAR_RIGHT.equals(hint)) {
            return rightBias(boundingBox);
        }
        if (CONTENT.equals(hint)) {
            return contentBias(boundingBox);
        }
        BBox box = BBox.parse(boundingBox);
        if (box == null) {
            return inHeaderRegion && hint.startsWith("TOP_") ? 0.7 : 0.35;
        }
        double cx = box.relX();
        double cy = box.relY();
        return switch (hint) {
            case TOP_LEFT -> proximity(cx, cy, 0.15, 0.12);
            case TOP_CENTER -> proximity(cx, cy, 0.5, 0.12);
            case TOP_RIGHT -> proximity(cx, cy, 0.88, 0.12) + (inHeaderRegion ? 0.08 : 0);
            case MIDDLE_LEFT -> proximity(cx, cy, 0.15, 0.5);
            case CENTER -> proximity(cx, cy, 0.5, 0.5);
            case MIDDLE_RIGHT -> proximity(cx, cy, 0.88, 0.5);
            case BOTTOM_LEFT -> proximity(cx, cy, 0.15, 0.88);
            case BOTTOM_CENTER -> proximity(cx, cy, 0.5, 0.88);
            case BOTTOM_RIGHT -> proximity(cx, cy, 0.88, 0.88);
            default -> 0.5;
        };
    }

    private static double proximity(double cx, double cy, double tx, double ty) {
        double dx = cx - tx;
        double dy = cy - ty;
        double dist = Math.sqrt(dx * dx + dy * dy);
        return Math.max(0, 1.0 - dist * 1.6);
    }

    private static double leftBias(String boundingBox) {
        BBox box = BBox.parse(boundingBox);
        return box == null ? 0.4 : Math.max(0, 1.0 - box.relX() * 2.2);
    }

    private static double rightBias(String boundingBox) {
        BBox box = BBox.parse(boundingBox);
        return box == null ? 0.4 : Math.max(0, (box.relX() - 0.55) * 2.2);
    }

    private static double contentBias(String boundingBox) {
        BBox box = BBox.parse(boundingBox);
        if (box == null) {
            return 0.5;
        }
        return proximity(box.relX(), box.relY(), 0.5, 0.45);
    }

    private static double centerBias(String boundingBox) {
        return contentBias(boundingBox);
    }

    private static double footerBias(String boundingBox) {
        BBox box = BBox.parse(boundingBox);
        return box == null ? 0.4 : Math.max(0, (box.relY() - 0.7) * 3.0);
    }

    private static boolean isNullToken(String raw) {
        String t = raw.trim();
        return "null".equalsIgnoreCase(t) || "undefined".equalsIgnoreCase(t) || "none".equalsIgnoreCase(t);
    }

    private record BBox(double x, double y, double w, double h) {
        static BBox parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return null;
            }
            try {
                String[] parts = raw.split(",");
                if (parts.length < 4) {
                    return null;
                }
                return new BBox(
                        Double.parseDouble(parts[0].trim()),
                        Double.parseDouble(parts[1].trim()),
                        Double.parseDouble(parts[2].trim()),
                        Double.parseDouble(parts[3].trim())
                );
            } catch (RuntimeException ex) {
                return null;
            }
        }

        double relX() {
            double vw = Math.max(800, x + w + 40);
            return Math.min(1.0, Math.max(0, (x + w / 2.0) / vw));
        }

        double relY() {
            double vh = Math.max(600, y + h + 40);
            return Math.min(1.0, Math.max(0, (y + h / 2.0) / vh));
        }
    }
}
