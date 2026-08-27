package com.smartqa.browser.multimodal;

import tools.jackson.databind.JsonNode;

/**
 * Screenshot-relative region. Evidence only — never a Playwright click coordinate.
 */
public record VisualRegion(int x, int y, int width, int height) {

    public static VisualRegion fromJson(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        JsonNode region = node.has("visualRegion") ? node.get("visualRegion") : node;
        if (region == null || region.isMissingNode() || region.isNull()) {
            return null;
        }
        int x = region.path("x").asInt(Integer.MIN_VALUE);
        int y = region.path("y").asInt(Integer.MIN_VALUE);
        int w = region.path("width").asInt(region.path("w").asInt(0));
        int h = region.path("height").asInt(region.path("h").asInt(0));
        if (x == Integer.MIN_VALUE || y == Integer.MIN_VALUE || w <= 0 || h <= 0) {
            return null;
        }
        return new VisualRegion(x, y, w, h);
    }

    public boolean overlaps(String boundingBox) {
        int[] box = VisualRegionAnalyzer.parseBox(boundingBox);
        if (box[2] <= 0 || box[3] <= 0 || width <= 0 || height <= 0) {
            return false;
        }
        int ax2 = x + width;
        int ay2 = y + height;
        int bx2 = box[0] + box[2];
        int by2 = box[1] + box[3];
        return x < bx2 && ax2 > box[0] && y < by2 && ay2 > box[1];
    }

    public boolean containsCenterOf(String boundingBox) {
        int[] box = VisualRegionAnalyzer.parseBox(boundingBox);
        if (box[2] <= 0 || box[3] <= 0) {
            return false;
        }
        int cx = box[0] + box[2] / 2;
        int cy = box[1] + box[3] / 2;
        return cx >= x && cx <= x + width && cy >= y && cy <= y + height;
    }
}
