package com.smartqa.intent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Corrects harmless spelling mistakes in tester language.
 * Never rewrites quoted literals, product names, SKUs, or quantities.
 */
public final class LanguageNormalizer {

    private static final Map<Pattern, String> HARMLESS = new LinkedHashMap<>();
    private static final Pattern QUOTED = Pattern.compile("(\"[^\"]*\"|'[^']*')");

    static {
        map("\\binsite\\b", "inside");
        map("\\binsid\\b", "inside");
        map("\\bwithn\\b", "within");
        map("\\bwithen\\b", "within");
        map("\\bselct\\b", "select");
        map("\\bslect\\b", "select");
        map("\\bselet\\b", "select");
        map("\\bseach\\b", "search");
        map("\\bserach\\b", "search");
        map("\\bsrch\\b", "search");
        map("\\bclcik\\b", "click");
        map("\\bclik\\b", "click");
        map("\\bclck\\b", "click");
        map("\\bclicl\\b", "click");
        map("\\bclikc\\b", "click");
        map("\\bopne\\b", "open");
        map("\\bnaviagte\\b", "navigate");
        map("\\bnavigte\\b", "navigate");
        map("\\bverfy\\b", "verify");
        map("\\bverfiy\\b", "verify");
        map("\\bverifiy\\b", "verify");
        map("\\bchekbox\\b", "checkbox");
        map("\\bcheckbx\\b", "checkbox");
        map("\\bchek\\s*box\\b", "checkbox");
        map("\\bdropdwon\\b", "dropdown");
        map("\\bdrpdown\\b", "dropdown");
        map("\\bfliter\\b", "filter");
        map("\\bfiltr\\b", "filter");
        map("\\bquantiy\\b", "quantity");
        map("\\bhver\\b", "hover");
        map("\\bscrol\\b", "scroll");
        map("\\bsubmt\\b", "submit");
        map("\\bsubimt\\b", "submit");
        map("\\benterr\\b", "enter");
    }

    private LanguageNormalizer() {
    }

    public static String normalize(String line) {
        if (line == null || line.isBlank()) {
            return line;
        }
        Matcher literals = QUOTED.matcher(line);
        String[] parts = QUOTED.split(line, -1);
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (true) {
            out.append(applyHarmless(parts[i]));
            if (!literals.find()) {
                break;
            }
            out.append(literals.group());
            i++;
            if (i >= parts.length) {
                break;
            }
        }
        return out.toString().replaceAll("\\s+", " ").trim();
    }

    private static String applyHarmless(String text) {
        String result = text;
        for (Map.Entry<Pattern, String> entry : HARMLESS.entrySet()) {
            result = entry.getKey().matcher(result).replaceAll(entry.getValue());
        }
        return result;
    }

    private static void map(String regex, String replacement) {
        HARMLESS.put(Pattern.compile(regex, Pattern.CASE_INSENSITIVE), replacement);
    }
}
