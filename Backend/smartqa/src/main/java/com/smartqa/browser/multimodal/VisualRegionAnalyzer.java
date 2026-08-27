package com.smartqa.browser.multimodal;

import com.smartqa.browser.intelligence.ElementCandidate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Coarse layout regions from bounding boxes. Location is evidence, not a selector.
 */
public final class VisualRegionAnalyzer {

    public enum GridRegion {
        TOP_LEFT, TOP_CENTER, TOP_RIGHT,
        MIDDLE_LEFT, CENTER, MIDDLE_RIGHT,
        BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT,
        UNKNOWN
    }

    public record RegionAssignment(
            String candidateId,
            GridRegion grid,
            String semanticRegion,
            int x,
            int y,
            int width,
            int height
    ) {
    }

    private VisualRegionAnalyzer() {
    }

    public static List<RegionAssignment> assign(List<ElementCandidate> elements, int viewportWidth, int viewportHeight) {
        int vw = Math.max(1, viewportWidth);
        int vh = Math.max(1, viewportHeight);
        List<RegionAssignment> out = new ArrayList<>();
        if (elements == null) {
            return List.of();
        }
        for (ElementCandidate el : elements) {
            int[] box = parseBox(el.boundingBox());
            GridRegion grid = gridOf(box[0], box[1], vw, vh);
            out.add(new RegionAssignment(
                    el.candidateId(),
                    grid,
                    semanticRegion(el, grid),
                    box[0], box[1], box[2], box[3]
            ));
        }
        return out;
    }

    public static String compact(List<RegionAssignment> assignments, int limit) {
        if (assignments == null || assignments.isEmpty()) {
            return "(no regions)";
        }
        StringBuilder sb = new StringBuilder();
        int n = Math.min(Math.max(1, limit), assignments.size());
        for (int i = 0; i < n; i++) {
            RegionAssignment a = assignments.get(i);
            sb.append(a.candidateId())
                    .append(" | grid=").append(a.grid())
                    .append(" | region=").append(a.semanticRegion())
                    .append(" | xy=").append(a.x()).append(',').append(a.y())
                    .append('\n');
        }
        return sb.toString();
    }

    public static GridRegion gridOf(int x, int y, int viewportWidth, int viewportHeight) {
        if (viewportWidth <= 0 || viewportHeight <= 0) {
            return GridRegion.UNKNOWN;
        }
        int col = x < viewportWidth / 3 ? 0 : (x < 2 * viewportWidth / 3 ? 1 : 2);
        int row = y < viewportHeight / 3 ? 0 : (y < 2 * viewportHeight / 3 ? 1 : 2);
        return switch (row * 3 + col) {
            case 0 -> GridRegion.TOP_LEFT;
            case 1 -> GridRegion.TOP_CENTER;
            case 2 -> GridRegion.TOP_RIGHT;
            case 3 -> GridRegion.MIDDLE_LEFT;
            case 4 -> GridRegion.CENTER;
            case 5 -> GridRegion.MIDDLE_RIGHT;
            case 6 -> GridRegion.BOTTOM_LEFT;
            case 7 -> GridRegion.BOTTOM_CENTER;
            case 8 -> GridRegion.BOTTOM_RIGHT;
            default -> GridRegion.UNKNOWN;
        };
    }

    static String semanticRegion(ElementCandidate el, GridRegion grid) {
        String declared = el.region() == null ? "" : el.region().toUpperCase(Locale.ROOT);
        if (!declared.isBlank()) {
            return declared;
        }
        if (el.inHeaderRegion() || grid == GridRegion.TOP_LEFT || grid == GridRegion.TOP_CENTER || grid == GridRegion.TOP_RIGHT) {
            if (el.inHeaderRegion()) {
                return "HEADER";
            }
        }
        return switch (grid) {
            case TOP_LEFT, MIDDLE_LEFT, BOTTOM_LEFT -> "SIDEBAR";
            case TOP_RIGHT, MIDDLE_RIGHT -> "HEADER";
            default -> "CONTENT";
        };
    }

    static int[] parseBox(String boundingBox) {
        int[] box = {0, 0, 0, 0};
        if (boundingBox == null || boundingBox.isBlank()) {
            return box;
        }
        String[] parts = boundingBox.split("[,x\\s]+");
        for (int i = 0; i < Math.min(4, parts.length); i++) {
            try {
                box[i] = Integer.parseInt(parts[i].trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return box;
    }
}
