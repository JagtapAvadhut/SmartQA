package com.smartqa.common;

public final class NaturalLanguage {

    private NaturalLanguage() {
    }

    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.replace("\r\n", "\n").trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        return collapseRepeated(trimmed);
    }

    static String collapseRepeated(String text) {
        String[] lines = text.split("\n", -1);
        String first = null;
        int firstIdx = -1;
        for (int i = 0; i < lines.length; i++) {
            if (!lines[i].trim().isEmpty()) {
                first = lines[i].trim();
                firstIdx = i;
                break;
            }
        }
        if (first != null && first.length() >= 8) {
            for (int i = firstIdx + 1; i < lines.length; i++) {
                if (!first.equals(lines[i].trim())) {
                    continue;
                }
                String firstBlock = join(lines, 0, i).trim();
                String secondBlock = join(lines, i, lines.length).trim();
                if (!firstBlock.isEmpty() && firstBlock.equals(secondBlock)) {
                    return firstBlock;
                }
                break;
            }
        }
        return collapseHalves(text);
    }

    private static String collapseHalves(String text) {
        String compact = text.trim();
        int mid = compact.length() / 2;
        if (mid < 40) {
            return compact;
        }
        for (String sep : new String[]{"\n\n", "\n", ""}) {
            int split = compact.indexOf(sep, mid - Math.min(80, mid));
            if (split < 20) {
                continue;
            }
            String left = compact.substring(0, split).trim();
            String right = compact.substring(split + sep.length()).trim();
            if (!left.isEmpty() && left.equals(right)) {
                return left;
            }
        }
        return compact;
    }

    private static String join(String[] lines, int from, int to) {
        StringBuilder builder = new StringBuilder();
        for (int i = from; i < to; i++) {
            if (i > from) {
                builder.append('\n');
            }
            builder.append(lines[i]);
        }
        return builder.toString();
    }
}
